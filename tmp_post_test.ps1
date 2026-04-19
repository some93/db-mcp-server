# POST 1: root_db user_summary
$body1 = '{"datasourceName":"root_db","sql":"SELECT * FROM sys.user_summary LIMIT 3"}'
try {
    $r1 = Invoke-WebRequest -Uri 'http://127.0.0.1:11198/api/query' -Method POST -Body $body1 -ContentType 'application/json' -UseBasicParsing
    "[POST1_START]" | Out-File -FilePath 'd:\Workspace\db-mcp-server\tmp_results.txt' -Encoding UTF8
    $r1.Content | Out-File -FilePath 'd:\Workspace\db-mcp-server\tmp_results.txt' -Encoding UTF8 -Append
    "[POST1_END]" | Out-File -FilePath 'd:\Workspace\db-mcp-server\tmp_results.txt' -Encoding UTF8 -Append
} catch {
    "[POST1_ERROR]" | Out-File -FilePath 'd:\Workspace\db-mcp-server\tmp_results.txt' -Encoding UTF8
    $_.Exception.Message | Out-File -FilePath 'd:\Workspace\db-mcp-server\tmp_results.txt' -Encoding UTF8 -Append
    "[POST1_END]" | Out-File -FilePath 'd:\Workspace\db-mcp-server\tmp_results.txt' -Encoding UTF8 -Append
}

# POST 2: some_db information_schema
$body2 = '{"datasourceName":"some_db","sql":"SELECT table_name FROM information_schema.tables WHERE table_schema=''ry'' LIMIT 5"}'
try {
    $r2 = Invoke-WebRequest -Uri 'http://127.0.0.1:11198/api/query' -Method POST -Body $body2 -ContentType 'application/json' -UseBasicParsing
    "[POST2_START]" | Out-File -FilePath 'd:\Workspace\db-mcp-server\tmp_results.txt' -Encoding UTF8 -Append
    $r2.Content | Out-File -FilePath 'd:\Workspace\db-mcp-server\tmp_results.txt' -Encoding UTF8 -Append
    "[POST2_END]" | Out-File -FilePath 'd:\Workspace\db-mcp-server\tmp_results.txt' -Encoding UTF8 -Append
} catch {
    "[POST2_ERROR]" | Out-File -FilePath 'd:\Workspace\db-mcp-server\tmp_results.txt' -Encoding UTF8 -Append
    $_.Exception.Message | Out-File -FilePath 'd:\Workspace\db-mcp-server\tmp_results.txt' -Encoding UTF8 -Append
    "[POST2_END]" | Out-File -FilePath 'd:\Workspace\db-mcp-server\tmp_results.txt' -Encoding UTF8 -Append
}
