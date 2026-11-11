param(
    [uri]$BaseUrl = $env:RAG_OLLAMA_BASE_URL,
    [switch]$RunInference
)

$ErrorActionPreference = 'Stop'
if (-not $BaseUrl -or $BaseUrl.Scheme -notin @('http', 'https') -or
        -not $BaseUrl.Host -or $BaseUrl.UserInfo -or $BaseUrl.Query -or $BaseUrl.Fragment) {
    throw 'Set RAG_OLLAMA_BASE_URL or pass -BaseUrl with an HTTP(S) server URL without credentials.'
}
$baseAddress = $BaseUrl.AbsoluteUri.TrimEnd('/')
$embeddingModel = 'qwen3-embedding:8b'
$generationModel = if ($env:RAG_GENERATION_MODEL) { $env:RAG_GENERATION_MODEL } else { 'qwen3:8b' }

$tags = Invoke-RestMethod -Uri "$baseAddress/api/tags" -TimeoutSec 10
$modelNames = @($tags.models | ForEach-Object { $_.name })
foreach ($modelName in @($embeddingModel, $generationModel)) {
    if ($modelName -notin $modelNames) { throw "Required model is missing: $modelName" }
}
Write-Host "Connection OK: $baseAddress"
Write-Host "Models installed: $embeddingModel, $generationModel"

if ($RunInference) {
    $embeddingBody = @{
        model = $embeddingModel
        input = @('RAG connection check')
        dimensions = 4096
        truncate = $false
        options = @{ num_ctx = 2048 }
    } | ConvertTo-Json -Depth 5
    $embedding = Invoke-RestMethod -Method Post -Uri "$baseAddress/api/embed" -ContentType 'application/json' -Body $embeddingBody -TimeoutSec 180
    if ($embedding.model -ne $embeddingModel -or $embedding.embeddings.Count -ne 1 -or $embedding.embeddings[0].Count -ne 4096) {
        throw 'Embedding model, count or dimensions do not match the backend.'
    }
    $nonZero = $false
    foreach ($component in $embedding.embeddings[0]) {
        if ($null -eq $component -or [double]::IsNaN([double]$component) -or
                [double]::IsInfinity([double]$component) -or [single]::IsInfinity([single]$component)) {
            throw 'Embedding contains invalid values.'
        }
        if ([single]$component -ne 0) { $nonZero = $true }
    }
    if (-not $nonZero) { throw 'Embedding is a zero vector.' }
    Write-Host 'Embedding inference OK: 4096 dimensions'

    $chatBody = @{
        model = $generationModel
        stream = $false
        think = $false
        messages = @(@{ role = 'user'; content = 'Reply with the single word ready.' })
        options = @{ temperature = 0; num_predict = 128; num_ctx = 4096 }
    } | ConvertTo-Json -Depth 5
    $chat = Invoke-RestMethod -Method Post -Uri "$baseAddress/api/chat" -ContentType 'application/json' -Body $chatBody -TimeoutSec 180
    if ($chat.model -ne $generationModel -or -not $chat.done -or $chat.done_reason -ne 'stop' -or
            $chat.message.role -ne 'assistant' -or [string]::IsNullOrWhiteSpace($chat.message.content)) {
        throw 'Generation model did not return a complete answer.'
    }
    Write-Host 'Answer inference OK'
}

Write-Host 'Set these values in the backend process or IDE Run Configuration, then restart the backend:'
Write-Host "RAG_OLLAMA_BASE_URL=$baseAddress"
Write-Host 'RAG_QA_PROTOTYPE_ENABLED=true'
Write-Host "RAG_GENERATION_MODEL=$generationModel"
