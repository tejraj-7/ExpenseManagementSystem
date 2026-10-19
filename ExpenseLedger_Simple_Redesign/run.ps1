# =====================================================================
# SPENDWISE AUTOMATED TOMCAT BUILD & RUN SCRIPT
# =====================================================================
$env:CATALINA_HOME = "D:\An Project of Java\New folder\apache-tomcat-10.1.61"
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
$Proj = "D:\Project_2\ExpenseLedger_Simple_Redesign"

Write-Host "[1/5] Cleaning UTF-8 BOM encoding on Java files..." -ForegroundColor Cyan
Get-ChildItem -Path "$Proj\src\app" -Filter "*.java" | ForEach-Object {
    $content = Get-Content $_.FullName -Raw -Encoding UTF8
    [System.IO.File]::WriteAllText($_.FullName, $content, (New-Object System.Text.UTF8Encoding $false))
}

Write-Host "[2/5] Compiling Java Servlet classes..." -ForegroundColor Cyan
& "$env:JAVA_HOME\bin\javac.exe" -cp "$env:CATALINA_HOME\lib\servlet-api.jar" -d "$Proj\web\WEB-INF\classes" "$Proj\src\app\*.java"
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Compilation failed! Check your Java syntax." -ForegroundColor Red
    pause
    exit
}

Write-Host "[3/5] Deploying build artifacts to Apache Tomcat..." -ForegroundColor Cyan
$TomcatAppDir = "$env:CATALINA_HOME\webapps\ExpenseLedger_Simple"
if (!(Test-Path $TomcatAppDir)) {
    New-Item -ItemType Directory -Force -Path $TomcatAppDir | Out-Null
}
Copy-Item -Path "$Proj\web\*" -Destination $TomcatAppDir -Recurse -Force

Write-Host "[4/5] Shutting down and restarting Apache Tomcat server..." -ForegroundColor Cyan
& "$env:CATALINA_HOME\bin\shutdown.bat" | Out-Null
Start-Sleep -Seconds 3
& "$env:CATALINA_HOME\bin\startup.bat" | Out-Null
Start-Sleep -Seconds 4

Write-Host "[5/5] Opening SpendWise in your default browser..." -ForegroundColor Cyan
Start-Process "http://localhost:8080/ExpenseLedger_Simple/"

Write-Host "`n[SUCCESS] SpendWise is live and running!" -ForegroundColor Green
pause