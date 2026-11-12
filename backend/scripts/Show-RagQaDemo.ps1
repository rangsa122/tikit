param([switch]$SkipRun)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$originalLiveFlag = $env:RAG_QA_ANSWER_LIVE_TEST

Push-Location -LiteralPath $projectRoot
try {
    if (-not $SkipRun) {
        $env:RAG_QA_ANSWER_LIVE_TEST = 'true'
        & .\gradlew.bat test --tests 'com.tikit.rag.generation.RagAnswerLiveTest' --rerun-tasks
        if ($LASTEXITCODE -ne 0) { throw '실제 모델 검증에 실패했습니다. Gradle 테스트 결과를 확인해 주세요.' }
    }

    $reportPath = Join-Path $projectRoot 'build/test-results/test/TEST-com.tikit.rag.generation.RagAnswerLiveTest.xml'
    if (-not (Test-Path -LiteralPath $reportPath)) { throw '실모델 테스트 결과가 없습니다. -SkipRun 없이 실행해 주세요.' }
    [xml]$report = Get-Content -LiteralPath $reportPath -Raw -Encoding UTF8
    if ([int]$report.testsuite.failures -gt 0 -or [int]$report.testsuite.errors -gt 0 -or [int]$report.testsuite.skipped -gt 0) {
        throw '실모델 테스트가 실패했거나 생략되었습니다. 테스트 결과를 확인해 주세요.'
    }
    $results = @($report.testsuite.'system-out'.InnerText -split '\r?\n' |
            Where-Object { $_.StartsWith('RAG_DEMO_RESULT=') } |
            ForEach-Object { $_.Substring('RAG_DEMO_RESULT='.Length) | ConvertFrom-Json })
    if ($results.Count -lt 3) { throw '대표 질문 출력이 부족합니다. -SkipRun 없이 다시 실행해 주세요.' }

    $lines = [Collections.Generic.List[string]]::new()
    $lines.Add('# RAG 실제 모델 실행 결과')
    $lines.Add('')
    $lines.Add('테스트 실행 시각(UTC): ' + $report.testsuite.timestamp)
    $lines.Add('')
    $lines.Add('로컬 Ollama의 실제 임베딩·답변 모델을 호출한 결과입니다. API 서버를 띄우지 않고 API가 사용하는 검색·답변 서비스를 직접 실행했습니다.')
    $lines.Add('')
    if ($results.Count -gt 3) {
        $lines.Add('마지막 복합 질문은 테스트에서만 검색 하한을 낮춰 후보를 강제로 검색한 사례입니다. 실제 API 기본 설정은 바꾸지 않았습니다.')
        $lines.Add('')
    }
    foreach ($result in $results) {
        $lines.Add('## ' + $result.question)
        $lines.Add('')
        $lines.Add($result.answer)
        $lines.Add('')
        Write-Host "`n질문: $($result.question)" -ForegroundColor Cyan
        Write-Host "답변: $($result.answer)"
        if ($result.sources.Count -eq 0) {
            $lines.Add('출처: 없음')
            Write-Host '출처: 없음'
        } else {
            foreach ($source in $result.sources) {
                $lines.Add('- 출처: ' + $source.documentTitle + ' (`' + $source.documentId + '`)')
                Write-Host "출처: $($source.documentTitle) / $($source.documentId)" -ForegroundColor Green
            }
        }
        $lines.Add('')
        $lines.Add('검색된 청크 수: ' + $result.matches.Count)
        $lines.Add('')
        $lines.Add('<details><summary>실제 응답 데이터 보기</summary>')
        $lines.Add('')
        $lines.Add('```json')
        $lines.Add(($result | ConvertTo-Json -Depth 10))
        $lines.Add('```')
        $lines.Add('')
        $lines.Add('</details>')
        $lines.Add('')
    }
    $outputPath = Join-Path $projectRoot 'build/rag-qa-demo-results.md'
    [IO.File]::WriteAllLines($outputPath, $lines, [Text.UTF8Encoding]::new($false))
    Write-Host "`n결과 파일: $outputPath"
} finally {
    $env:RAG_QA_ANSWER_LIVE_TEST = $originalLiveFlag
    Pop-Location
}
