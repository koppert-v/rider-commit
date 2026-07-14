[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ArtifactDirectory,

    [Parameter(Mandatory = $true)]
    [string]$OutputDirectory,

    [Parameter(Mandatory = $true)]
    [string]$BaseUrl,

    [Parameter(Mandatory = $true)]
    [string]$Tag
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$gradlePropertiesPath = Join-Path $repositoryRoot "gradle.properties"
$pluginXmlPath = Join-Path $repositoryRoot "src/main/resources/META-INF/plugin.xml"
$buildScriptPath = Join-Path $repositoryRoot "build.gradle.kts"

function Get-GradleProperty {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Path,

        [Parameter(Mandatory = $true)]
        [string]$Name
    )

    $line = Get-Content -LiteralPath $Path |
        Where-Object { $_ -match "^\s*$([regex]::Escape($Name))\s*=" } |
        Select-Object -First 1

    if ($null -eq $line) {
        throw "Gradle property '$Name' was not found in '$Path'."
    }

    return ($line -split "=", 2)[1].Trim()
}

function Get-XmlText {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Value
    )

    if ($Value -is [System.Xml.XmlNode]) {
        return $Value.InnerText.Trim()
    }

    return ([string]$Value).Trim()
}

$version = Get-GradleProperty -Path $gradlePropertiesPath -Name "pluginVersion"
$expectedTag = "v$version"
if ($Tag -ne $expectedTag) {
    throw "Release tag '$Tag' does not match plugin version '$version'. Expected '$expectedTag'."
}

[xml]$pluginDescriptor = Get-Content -LiteralPath $pluginXmlPath -Raw
$plugin = $pluginDescriptor."idea-plugin"
$pluginId = Get-XmlText -Value $plugin.id
$pluginName = Get-XmlText -Value $plugin.name
$pluginDescription = Get-XmlText -Value $plugin.description

$buildScript = Get-Content -LiteralPath $buildScriptPath -Raw
$sinceBuildMatch = [regex]::Match($buildScript, 'sinceBuild\s*=\s*"([^"]+)"')
if (-not $sinceBuildMatch.Success) {
    throw "sinceBuild was not found in '$buildScriptPath'."
}
$sinceBuild = $sinceBuildMatch.Groups[1].Value

$archives = @(Get-ChildItem -LiteralPath $ArtifactDirectory -Filter "*.zip" -File)
if ($archives.Count -ne 1) {
    throw "Expected exactly one plugin ZIP in '$ArtifactDirectory', found $($archives.Count)."
}

if (Test-Path -LiteralPath $OutputDirectory) {
    throw "Output directory '$OutputDirectory' already exists. Use an empty path."
}

$normalizedBaseUrl = $BaseUrl.TrimEnd("/")
$archive = $archives[0]
$encodedArchiveName = [uri]::EscapeDataString($archive.Name)
$downloadUrl = "$normalizedBaseUrl/plugins/$encodedArchiveName"
$repositoryUrl = "$normalizedBaseUrl/updatePlugins.xml"

$pluginsDirectory = Join-Path $OutputDirectory "plugins"
New-Item -ItemType Directory -Path $pluginsDirectory -Force | Out-Null
Copy-Item -LiteralPath $archive.FullName -Destination (Join-Path $pluginsDirectory $archive.Name)

$xmlSettings = [System.Xml.XmlWriterSettings]::new()
$xmlSettings.Encoding = [System.Text.UTF8Encoding]::new($false)
$xmlSettings.Indent = $true
$xmlSettings.NewLineChars = "`n"
$xmlSettings.NewLineHandling = [System.Xml.NewLineHandling]::Replace

$xmlPath = Join-Path $OutputDirectory "updatePlugins.xml"
$writer = [System.Xml.XmlWriter]::Create($xmlPath, $xmlSettings)
try {
    $writer.WriteStartDocument()
    $writer.WriteStartElement("plugins")
    $writer.WriteStartElement("plugin")
    $writer.WriteAttributeString("id", $pluginId)
    $writer.WriteAttributeString("url", $downloadUrl)
    $writer.WriteAttributeString("version", $version)
    $writer.WriteStartElement("idea-version")
    $writer.WriteAttributeString("since-build", $sinceBuild)
    $writer.WriteEndElement()
    $writer.WriteElementString("name", $pluginName)
    $writer.WriteStartElement("description")
    $writer.WriteCData($pluginDescription)
    $writer.WriteEndElement()
    $writer.WriteEndElement()
    $writer.WriteEndElement()
    $writer.WriteEndDocument()
}
finally {
    $writer.Dispose()
}

$htmlName = [System.Net.WebUtility]::HtmlEncode($pluginName)
$htmlVersion = [System.Net.WebUtility]::HtmlEncode($version)
$htmlRepositoryUrl = [System.Net.WebUtility]::HtmlEncode($repositoryUrl)
$htmlDownloadUrl = [System.Net.WebUtility]::HtmlEncode($downloadUrl)

$indexHtml = @"
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>$htmlName</title>
  <style>
    body { max-width: 760px; margin: 64px auto; padding: 0 24px; font: 16px/1.6 system-ui, sans-serif; color: #202124; }
    code { display: block; overflow-wrap: anywhere; padding: 12px; border-radius: 8px; background: #f3f4f6; }
    a { color: #2563eb; }
  </style>
</head>
<body>
  <h1>$htmlName</h1>
  <p>Latest version: <strong>$htmlVersion</strong></p>
  <p><a href="$htmlDownloadUrl">Download plugin ZIP</a></p>
  <h2>Rider plugin repository</h2>
  <p>Add this URL in Settings &rarr; Plugins &rarr; Manage Plugin Repositories:</p>
  <code>$htmlRepositoryUrl</code>
</body>
</html>
"@

$indexPath = Join-Path $OutputDirectory "index.html"
[System.IO.File]::WriteAllText($indexPath, $indexHtml, [System.Text.UTF8Encoding]::new($false))

Write-Host "Prepared plugin repository for $pluginId $version"
Write-Host "Repository URL: $repositoryUrl"
Write-Host "Plugin URL: $downloadUrl"
