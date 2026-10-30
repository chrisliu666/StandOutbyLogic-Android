param(
  [int]$Port = 5188
)

Set-Location -LiteralPath $PSScriptRoot
node .\dev-server.js $Port
