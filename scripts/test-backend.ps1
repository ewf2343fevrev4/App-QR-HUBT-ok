param([string]$PostgresBin='C:\Program Files\PostgreSQL\18\bin',[int]$Port=55432)
$ErrorActionPreference='Stop'
$workspace=Split-Path $PSScriptRoot -Parent
$testRoot=Join-Path $workspace ('.tools\pg-test-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
$dataPath=Join-Path $testRoot 'data'
& (Join-Path $PostgresBin 'initdb.exe') -D $dataPath -U postgres -A trust --encoding=UTF8 --locale=C > (Join-Path $testRoot 'init.log') 2>&1
if($LASTEXITCODE -ne 0) {throw 'initdb failed. See init.log.'}
$started=$false
try {
    $proc=Start-Process -FilePath (Join-Path $PostgresBin 'pg_ctl.exe') -ArgumentList @('-D',('"'+$dataPath+'"'),'-l',('"'+(Join-Path $testRoot 'server.log')+'"'),'-o',('"-p '+$Port+' -h 127.0.0.1"'),'-w','start') -WindowStyle Hidden -PassThru
    $proc.WaitForExit()
    if($proc.ExitCode -ne 0) {throw 'Private PostgreSQL test cluster could not start.'}
    $started=$true
    $psql=Join-Path $PostgresBin 'psql.exe'
    $common=@('-X','-h','127.0.0.1','-p',"$Port",'-U','postgres','-d','postgres','-v','ON_ERROR_STOP=1')
    foreach($file in @('supabase/tests/bootstrap.sql','supabase/migrations/202610260001_initial.sql','supabase/migrations/202610260002_import_students.sql','supabase/migrations/202610260003_separate_classes_sessions.sql','supabase/migrations/202610260004_paginate_students.sql','supabase/migrations/202610260005_fix_import_csv_update.sql','supabase/migrations/202610260006_fix_login_and_qr_scan.sql','supabase/migrations/202610260007_admin_get_students_page.sql','supabase/tests/integration.sql')) {
        & $psql @common -f (Join-Path $workspace $file)
        if($LASTEXITCODE -ne 0) {throw "SQL failed: $file"}
    }
    $processes=@()
    for($i=1;$i -le 12;$i++) {
        $sql=Join-Path $workspace 'supabase/tests/concurrent.sql'
        $processes+=Start-Process -FilePath $psql -ArgumentList ($common+@('-f',('"'+$sql+'"'),'-o',('"'+(Join-Path $testRoot "concurrent-$i.log")+'"'))) -WindowStyle Hidden -PassThru
    }
    foreach($p in $processes) {$p.WaitForExit(); if($p.ExitCode -ne 0) {throw 'Concurrent SQL request failed.'}}
    & $psql @common -f (Join-Path $workspace 'supabase/tests/finalize.sql')
    if($LASTEXITCODE -ne 0) {throw 'Final SQL assertions failed.'}
    Write-Output "PASSED: backend integration and 12 concurrent requests. Logs: $testRoot"
} finally {
    if($started) {& (Join-Path $PostgresBin 'pg_ctl.exe') -D $dataPath -m fast -w stop | Out-Null}
}
