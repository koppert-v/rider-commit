package dev.koppert.ridercommit.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.JBCardLayout
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import dev.koppert.ridercommit.AiProvider
import dev.koppert.ridercommit.ProviderIcons
import dev.koppert.ridercommit.settings.CodexCommitSettings.ProviderConfigurationState
import java.awt.Component
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.UUID
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

class CodexCommitConfigurable(private val project: Project) : SearchableConfigurable {
    private val providerConfigurations = mutableListOf<ProviderConfigurationState>()
    private val providerTableModel = ProviderTableModel(providerConfigurations)
    private val providerTable = JBTable(providerTableModel)
    private val activeProviderModel = DefaultComboBoxModel<ProviderConfigurationState>()
    private val activeProviderField = ComboBox(activeProviderModel)
    private val timeoutField = JBTextField()
    private val languageField = ComboBox(ProviderOptions.languages.toTypedArray()).apply {
        isEditable = true
    }
    private val maxDiffField = JBTextField()
    private val contextModeField = ComboBox(CommitContextMode.entries.toTypedArray())
    private val optimizeDiffCheckBox = JBCheckBox("Optimize diff mode for lower token usage")
    private val conventionalCommitsCheckBox = JBCheckBox("Use Conventional Commits format")
    private var panel: JPanel? = null

    override fun getId(): String = "dev.koppert.ridercommit.settings"

    override fun getDisplayName(): String = "AI Commit"

    override fun createComponent(): JComponent {
        activeProviderField.renderer = ProviderListRenderer()
        contextModeField.addActionListener { updateContextModeControls() }
        providerTable.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        providerTable.setShowGrid(false)
        providerTable.preferredScrollableViewportSize = JBUI.size(620, 150)
        providerTable.columnModel.getColumn(0).cellRenderer = ProviderNameCellRenderer()
        providerTable.columnModel.getColumn(0).preferredWidth = 170
        providerTable.columnModel.getColumn(0).maxWidth = 280
        providerTable.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2 && SwingUtilities.isLeftMouseButton(event)) {
                    editSelectedProvider()
                }
            }
        })

        val providersPanel = ToolbarDecorator.createDecorator(providerTable)
            .setAddAction { addProvider() }
            .setRemoveAction { removeSelectedProvider() }
            .setEditAction { editSelectedProvider() }
            .disableUpDownActions()
            .createPanel()
        providersPanel.preferredSize = JBUI.size(680, 205)

        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("LLM client:", activeProviderField, 1, false)
            .addComponent(providersPanel, 12)
            .addLabeledComponent("Commit language:", languageField, 1, false)
            .addLabeledComponent("Timeout, seconds:", timeoutField, 1, false)
            .addLabeledComponent("Context sent to AI:", contextModeField, 1, false)
            .addComponent(JBLabel("File-list mode is smaller, but may include unselected hunks from the listed files."), 6)
            .addLabeledComponent("Maximum diff characters:", maxDiffField, 1, false)
            .addComponent(optimizeDiffCheckBox, 4)
            .addComponent(JBLabel("Keeps changed lines with nearby context and balances the limit across files."), 8)
            .addComponent(conventionalCommitsCheckBox, 8)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        reset()
        return panel!!
    }

    override fun isModified(): Boolean {
        val state = CodexCommitSettings.getInstance(project).state
        val selectedProviderId = (activeProviderField.selectedItem as? ProviderConfigurationState)?.id.orEmpty()
        return selectedProviderId != state.activeProviderId ||
            !sameConfigurations(providerConfigurations, state.providerConfigurations) ||
            timeoutField.text.toIntOrNull() != state.timeoutSeconds ||
            comboText(languageField) != state.commitLanguage ||
            (contextModeField.selectedItem as? CommitContextMode)?.id != state.contextMode ||
            maxDiffField.text.toIntOrNull() != state.maxDiffCharacters ||
            optimizeDiffCheckBox.isSelected != state.optimizeDiff ||
            conventionalCommitsCheckBox.isSelected != state.useConventionalCommits
    }

    override fun apply() {
        val state = CodexCommitSettings.getInstance(project).state
        state.providerConfigurations = providerConfigurations.map { it.copyState() }.toMutableList()
        state.activeProviderId = (activeProviderField.selectedItem as? ProviderConfigurationState)?.id
            ?: state.providerConfigurations.first().id
        state.timeoutSeconds = timeoutField.text.toIntOrNull()?.coerceIn(10, 900) ?: 120
        state.commitLanguage = comboText(languageField).ifBlank { "English" }
        state.contextMode = (contextModeField.selectedItem as? CommitContextMode)?.id
            ?: CommitContextMode.FILE_LIST.id
        state.maxDiffCharacters = maxDiffField.text.toIntOrNull()?.coerceIn(1_000, 1_000_000) ?: 60_000
        state.optimizeDiff = optimizeDiffCheckBox.isSelected
        state.useConventionalCommits = conventionalCommitsCheckBox.isSelected
    }

    override fun reset() {
        val state = CodexCommitSettings.getInstance(project).state
        providerConfigurations.clear()
        providerConfigurations += state.providerConfigurations.map { it.copyState() }
        providerTableModel.fireTableDataChanged()
        refreshActiveProviderModel(state.activeProviderId)
        timeoutField.text = state.timeoutSeconds.toString()
        languageField.selectedItem = state.commitLanguage
        contextModeField.selectedItem = CommitContextMode.fromId(state.contextMode)
        maxDiffField.text = state.maxDiffCharacters.toString()
        optimizeDiffCheckBox.isSelected = state.optimizeDiff
        conventionalCommitsCheckBox.isSelected = state.useConventionalCommits
        updateContextModeControls()
    }

    override fun disposeUIResources() {
        panel = null
    }

    private fun addProvider() {
        val provider = AiProvider.CODEX
        val configuration = ProviderConfigurationState().also {
            it.id = UUID.randomUUID().toString()
            it.name = provider.displayName
            it.providerId = provider.id
            it.executablePath = provider.defaultExecutable
            it.effort = "medium"
        }
        val dialog = ProviderConfigurationDialog(project, configuration, true)
        if (!dialog.showAndGet()) return

        providerConfigurations += dialog.result()
        val row = providerConfigurations.lastIndex
        providerTableModel.fireTableRowsInserted(row, row)
        refreshActiveProviderModel(providerConfigurations[row].id)
        providerTable.selectionModel.setSelectionInterval(row, row)
    }

    private fun editSelectedProvider() {
        val row = providerTable.selectedRow
        if (row !in providerConfigurations.indices) return

        val dialog = ProviderConfigurationDialog(project, providerConfigurations[row], false)
        if (!dialog.showAndGet()) return

        val previousActiveId = (activeProviderField.selectedItem as? ProviderConfigurationState)?.id
        providerConfigurations[row] = dialog.result()
        providerTableModel.fireTableRowsUpdated(row, row)
        refreshActiveProviderModel(previousActiveId)
        providerTable.selectionModel.setSelectionInterval(row, row)
    }

    private fun removeSelectedProvider() {
        val row = providerTable.selectedRow
        if (row !in providerConfigurations.indices) return
        if (providerConfigurations.size == 1) {
            Messages.showInfoMessage(project, "At least one provider configuration is required.", "Provider Required")
            return
        }

        val activeId = (activeProviderField.selectedItem as? ProviderConfigurationState)?.id
        val removed = providerConfigurations.removeAt(row)
        providerTableModel.fireTableRowsDeleted(row, row)
        val nextActiveId = if (removed.id == activeId) providerConfigurations.first().id else activeId
        refreshActiveProviderModel(nextActiveId)
        val nextRow = row.coerceAtMost(providerConfigurations.lastIndex)
        providerTable.selectionModel.setSelectionInterval(nextRow, nextRow)
    }

    private fun refreshActiveProviderModel(preferredId: String?) {
        activeProviderModel.removeAllElements()
        providerConfigurations.forEach(activeProviderModel::addElement)
        activeProviderField.selectedItem = providerConfigurations.firstOrNull { it.id == preferredId }
            ?: providerConfigurations.firstOrNull()
    }

    private fun sameConfigurations(
        first: List<ProviderConfigurationState>,
        second: List<ProviderConfigurationState>,
    ): Boolean = first.size == second.size && first.zip(second).all { (left, right) ->
        left.id == right.id &&
            left.name == right.name &&
            left.providerId == right.providerId &&
            left.executablePath == right.executablePath &&
            left.model == right.model &&
            left.effort == right.effort &&
            left.skill == right.skill &&
            left.additionalPrompt == right.additionalPrompt
    }

    private fun comboText(comboBox: ComboBox<String>): String =
        comboBox.editor.item?.toString()?.trim().orEmpty()

    private fun updateContextModeControls() {
        val diffMode = contextModeField.selectedItem == CommitContextMode.DIFF
        maxDiffField.isEnabled = diffMode
        optimizeDiffCheckBox.isEnabled = diffMode
    }

    private class ProviderTableModel(
        private val configurations: List<ProviderConfigurationState>,
    ) : AbstractTableModel() {
        private val columns = arrayOf("Name", "Model")

        override fun getRowCount(): Int = configurations.size

        override fun getColumnCount(): Int = columns.size

        override fun getColumnName(column: Int): String = columns[column]

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val configuration = configurations[rowIndex]
            return when (columnIndex) {
                0 -> configuration
                1 -> configuration.model.ifBlank { "Default" }
                else -> ""
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = false
    }

    private class ProviderConfigurationDialog(
        project: Project,
        configuration: ProviderConfigurationState,
        private val isNew: Boolean,
    ) : DialogWrapper(project, true) {
        private val editors: List<ProviderEditor> = if (isNew) {
            AiProvider.entries.map { provider ->
                ProviderEditor(project, configuration.copyState().also {
                    it.name = provider.displayName
                    it.providerId = provider.id
                    it.executablePath = provider.defaultExecutable
                    it.model = ""
                    it.effort = "medium"
                    it.skill = ""
                    it.additionalPrompt = ""
                })
            }
        } else {
            listOf(ProviderEditor(project, configuration.copyState()))
        }
        private var selectedEditor = editors.first()

        init {
            title = if (isNew) "Add LLM Client" else "Edit LLM Client"
            setOKButtonText(if (isNew) "Add" else "Update")
            init()
        }

        override fun createCenterPanel(): JComponent {
            if (!isNew) return selectedEditor.panel

            val cardLayout = JBCardLayout()
            val cardPanel = JPanel(cardLayout)
            editors.forEach { editor ->
                cardPanel.add(editor.provider.id, editor.panel)
            }

            val providersList = JBList(editors.map { it.provider }).apply {
                cellRenderer = ProviderTypeListRenderer()
                selectionMode = ListSelectionModel.SINGLE_SELECTION
                addListSelectionListener { event ->
                    if (event.valueIsAdjusting) return@addListSelectionListener
                    val provider = selectedValue ?: return@addListSelectionListener
                    selectedEditor = editors.first { it.provider == provider }
                    cardLayout.show(cardPanel, provider.id)
                }
                selectedIndex = 0
            }

            return Splitter(false, 0.25f).apply {
                firstComponent = providersList
                secondComponent = cardPanel
                minimumSize = JBUI.size(650, 300)
                preferredSize = JBUI.size(700, 340)
            }
        }

        override fun doValidate(): ValidationInfo? = selectedEditor.validationInfo()

        fun result(): ProviderConfigurationState = selectedEditor.result()
    }

    private class ProviderEditor(
        project: Project,
        private val source: ProviderConfigurationState,
    ) {
        val provider = AiProvider.fromId(source.providerId)
        private val nameField = JBTextField(source.name)
        private val executablePathField = TextFieldWithBrowseButton()
        private val modelField = editableComboBox(ProviderOptions.models(provider), source.model)
        private val effortField = ComboBox(provider.supportedEfforts.toTypedArray())
        private val skillField = editableComboBox(SkillDiscovery.find(provider, project), source.skill)
        private val additionalPromptField = JBTextArea(source.additionalPrompt, 5, 40).apply {
            lineWrap = true
            wrapStyleWord = true
        }

        init {
            executablePathField.text = source.executablePath
            executablePathField.addBrowseFolderListener(
                "Select ${provider.displayName} executable",
                "Select the executable used by this provider configuration.",
                project,
                FileChooserDescriptorFactory.createSingleFileDescriptor(),
            )
            effortField.selectedItem = source.effort.takeIf { it in provider.supportedEfforts } ?: "medium"
        }

        private val skillExample = when (provider) {
            AiProvider.CODEX -> "commit-message (sent as \$commit-message)"
            AiProvider.CLAUDE_CODE -> "commit-message (sent as /commit-message)"
        }

        val panel: JPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Name:", nameField, 1, false)
            .addLabeledComponent("Executable:", executablePathField, 1, false)
            .addComponent(JBLabel("The diff is sent through stdin, not as a command-line argument."), 8)
            .addLabeledComponent("Model (optional):", modelField, 1, false)
            .addLabeledComponent("Effort:", effortField, 1, false)
            .addLabeledComponent("Skill (optional):", skillField, 1, false)
            .addComponent(JBLabel("Example: $skillExample"), 4)
            .addLabeledComponent("Additional prompt:", JBScrollPane(additionalPromptField), 1, true)
            .addComponentFillVertically(JPanel(), 0)
            .panel.apply {
                minimumSize = Dimension(520, 390)
            }

        fun validationInfo(): ValidationInfo? {
            if (nameField.text.trim().isEmpty()) {
                return ValidationInfo("Enter a configuration name", nameField)
            }
            if (executablePathField.text.trim().isEmpty()) {
                return ValidationInfo("Enter a CLI executable or command", executablePathField)
            }
            val model = comboText(modelField)
            if (model.isNotEmpty() && !model.matches(Regex("[A-Za-z0-9._:/\\[\\]-]+"))) {
                return ValidationInfo("Model contains unsupported characters", modelField)
            }
            val skill = comboText(skillField).removePrefix("$").removePrefix("/")
            if (skill.isNotEmpty() && !skill.matches(Regex("[A-Za-z0-9._:-]+"))) {
                return ValidationInfo("Use a skill name such as commit-message", skillField)
            }
            return null
        }

        fun result(): ProviderConfigurationState = source.copyState().also {
            it.name = nameField.text.trim()
            it.providerId = provider.id
            it.executablePath = executablePathField.text.trim().ifBlank { provider.defaultExecutable }
            it.model = comboText(modelField)
            it.effort = (effortField.selectedItem as? String).orEmpty()
            it.skill = comboText(skillField).removePrefix("$").removePrefix("/")
            it.additionalPrompt = additionalPromptField.text.trim()
        }

        companion object {
            private fun editableComboBox(options: List<String>, selectedValue: String): ComboBox<String> {
                val values = (listOf("") + options + selectedValue)
                    .distinct()
                    .toTypedArray()
                return ComboBox(values).apply {
                    isEditable = true
                    selectedItem = selectedValue
                }
            }

            private fun comboText(comboBox: ComboBox<String>): String =
                comboBox.editor.item?.toString()?.trim().orEmpty()
        }
    }

    private class ProviderListRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (value is ProviderConfigurationState) {
                text = value.name
                icon = ProviderIcons.get(AiProvider.fromId(value.providerId))
            }
            return component
        }
    }

    private class ProviderTypeListRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean,
        ): Component {
            val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (value is AiProvider) {
                text = value.displayName
                icon = ProviderIcons.get(value)
                border = JBUI.Borders.empty(4)
            }
            return component
        }
    }

    private class ProviderNameCellRenderer : DefaultTableCellRenderer() {
        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int,
        ): Component {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
            if (value is ProviderConfigurationState) {
                text = value.name
                icon = ProviderIcons.get(AiProvider.fromId(value.providerId))
                horizontalAlignment = SwingConstants.LEFT
            }
            return this
        }
    }
}
