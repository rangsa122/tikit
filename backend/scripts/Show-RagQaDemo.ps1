param([switch]$SkipRun)

$ErrorActionPreference = 'Stop'
$projectRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$originalLiveFlag = $env:RAG_QA_ANSWER_LIVE_TEST

Push-Location -LiteralPath $projectRoot
try {
    if (-not $SkipRun) {
        $env:RAG_QA_ANSWER_LIVE_TEST = 'true'
        & .\gradlew.bat localDbTest --tests 'com.tikit.rag.generation.RagAnswerLiveTest' --rerun-tasks
        if ($LASTEXITCODE -ne 0) { throw '실제 모델 검증에 실패했습니다. Gradle 테스트 결과를 확인해 주세요.' }
    }

    $reportPath = Join-Path $projectRoot 'build/test-results/localDbTest/TEST-com.tikit.rag.generation.RagAnswerLiveTest.xml'
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
    $lines.Add('임시 PostgreSQL 스키마에 저장한 문서를 로컬 Ollama의 실제 임베딩·답변 모델로 검색하고 답변한 결과입니다. 정상 검색, 활성 버전 교체, 근거 없음을 검증하며 DB 검색 실패는 MockMvc로 HTTP 503을 확인합니다.')
    $lines.Add('')
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
