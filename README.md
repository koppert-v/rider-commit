# AI Commit for Rider

Плагин добавляет кнопку **Generate with AI CLI** рядом с полем commit message в JetBrains Rider. Он берёт diff только отмеченных в окне Commit изменений, запускает установленный на компьютере Codex CLI или Claude Code и вставляет итоговый текст обратно в поле сообщения.

API key в плагине не хранится: используется существующая авторизация выбранного CLI.

## Требования

- JetBrains Rider 2024.2 или новее;
- установленный и авторизованный Codex CLI и/или Claude Code;
- команда выбранного CLI должна работать в терминале.

Проверка:

```powershell
codex --version
codex exec --help
(Get-Command codex).Source

claude --version
claude --help
(Get-Command claude).Source
```

## Запуск для разработки

```powershell
.\gradlew.bat runIde
```

Для сборки ZIP-плагина:

```powershell
.\gradlew.bat buildPlugin
```

Готовый архив появится в `build/distributions`.

## Настройка

Откройте **Settings | Tools | AI Commit**.

Настройки общие для Rider и применяются ко всем открытым проектам.

- **LLM client** — активная конфигурация для следующей генерации.
- Кнопка **+** добавляет конфигурацию Codex CLI или Claude Code.
- Кнопка редактирования меняет имя, тип CLI, executable, модель и effort.
- Можно создать несколько конфигураций одного провайдера с разными моделями.
- **Timeout** — максимальное время ожидания CLI.
- **Commit language** — язык результата, например `English` или `Russian`.
- **Maximum diff characters** — ограничение размера diff, передаваемого Codex.

Большой prompt передаётся обоим CLI через stdin, а не через аргумент командной строки. Codex запускается с `--ephemeral` и sandbox `read-only`. Claude Code запускается без сохранения сессии и с отключёнными инструментами, поэтому генераторы не получают права изменять проект.

## Благодарности и лицензии

Структура интерфейса управления LLM-клиентами адаптирована из [AI Commits](https://github.com/blarc/ai-commits-intellij-plugin), распространяемого по лицензии MIT. Текст лицензии включён в ресурсы плагина: `third-party/AI_COMMITS_LICENSE.txt`.
