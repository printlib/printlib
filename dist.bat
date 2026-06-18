@echo off
setlocal EnableExtensions EnableDelayedExpansion

cd /d "%~dp0"

set "TARGET_REMOTE=%DIST_REMOTE%"
if not defined TARGET_REMOTE set "TARGET_REMOTE=origin"

set "TARGET_BRANCH=%DIST_BRANCH%"
if not defined TARGET_BRANCH set "TARGET_BRANCH=cdn"

set "SOURCE_FILE="

if not "%~1"=="" set "SOURCE_FILE=%~1"
if not defined SOURCE_FILE if defined DIST_SOURCE set "SOURCE_FILE=%DIST_SOURCE%"

if not defined SOURCE_FILE (
  set "AUTO_SOURCE_COUNT=0"
  for %%F in (js\*.js) do (
    set /a AUTO_SOURCE_COUNT+=1
    if !AUTO_SOURCE_COUNT! EQU 1 set "SOURCE_FILE=%%~fF"
  )

  if !AUTO_SOURCE_COUNT! GTR 1 (
    echo ERROR: Multiple JavaScript source files found under js\.
    echo Run: dist.bat path\to\file.js
    echo Or set DIST_SOURCE.
    exit /b 1
  )
)

if not defined SOURCE_FILE (
  echo ERROR: Could not determine source JavaScript file.
  echo Provide one as: dist.bat path\to\file.js
  echo Or set DIST_SOURCE.
  exit /b 1
)

if not exist "%SOURCE_FILE%" (
  echo ERROR: Missing source file "%SOURCE_FILE%"
  exit /b 1
)

for %%F in ("%SOURCE_FILE%") do (
  set "SOURCE_NAME=%%~nxF"
  set "BASE_NAME=%%~nF"
)

for /f "usebackq delims=" %%I in (`git remote get-url "%TARGET_REMOTE%" 2^>nul`) do set "REMOTE_URL=%%I"

for /f "usebackq delims=" %%I in (`powershell -NoProfile -Command "$url = '%REMOTE_URL%'; if ($url -match 'github\.com[:/](?<repo>[^/]+/[^/.]+)(?:\.git)?$') { $matches['repo'] }"`) do set "REPO_SLUG=%%I"

where node >nul 2>&1
if errorlevel 1 (
  echo ERROR: node is required but was not found in PATH.
  exit /b 1
)

where npm >nul 2>&1
if errorlevel 1 (
  echo ERROR: npm is required but was not found in PATH.
  exit /b 1
)

echo Building dist assets...
if exist dist rmdir /s /q dist
mkdir dist
copy /y "%SOURCE_FILE%" "dist\%SOURCE_NAME%" >nul
call npx terser "%SOURCE_FILE%" -o "dist\%BASE_NAME%.min.js" --compress --mangle
if errorlevel 1 exit /b 1

for %%F in ("dist\%SOURCE_NAME%" "dist\%BASE_NAME%.min.js") do echo %%~nxF: %%~zF bytes

echo Publishing dist assets to %TARGET_REMOTE%/%TARGET_BRANCH%...
git config user.name "dist-publisher" >nul
git config user.email "dist-publisher@users.noreply.github.com" >nul

for /f "usebackq delims=" %%I in (`powershell -NoProfile -Command "$p = Join-Path $env:TEMP ('dist-publish-' + [guid]::NewGuid().ToString()); New-Item -ItemType Directory -Path $p ^| Out-Null; Write-Output $p"`) do set "PUBLISH_DIR=%%I"

if not defined PUBLISH_DIR (
  echo ERROR: Failed to create temp publish directory.
  exit /b 1
)

git ls-remote --exit-code --heads "%TARGET_REMOTE%" "%TARGET_BRANCH%" >nul 2>&1
if errorlevel 1 (
  git show-ref --verify --quiet "refs/heads/%TARGET_BRANCH%"
  if errorlevel 1 (
    git worktree add -b "%TARGET_BRANCH%" "%PUBLISH_DIR%" HEAD
    if errorlevel 1 goto :cleanup
    pushd "%PUBLISH_DIR%"
    git checkout --orphan "%TARGET_BRANCH%"
    if errorlevel 1 (
      popd
      goto :cleanup
    )
    popd
  ) else (
    git worktree add "%PUBLISH_DIR%" "%TARGET_BRANCH%"
    if errorlevel 1 goto :cleanup
  )
) else (
  git show-ref --verify --quiet "refs/heads/%TARGET_BRANCH%"
  if errorlevel 1 (
    git branch --track "%TARGET_BRANCH%" "%TARGET_REMOTE%/%TARGET_BRANCH%"
    if errorlevel 1 goto :cleanup
  )
  git worktree add "%PUBLISH_DIR%" "%TARGET_BRANCH%"
  if errorlevel 1 goto :cleanup
)

powershell -NoProfile -Command "$items = Get-ChildItem -Force '%PUBLISH_DIR%'; foreach ($item in $items) { if ($item.Name -ne '.git') { Remove-Item -Recurse -Force $item.FullName } }"
if errorlevel 1 goto :cleanup

mkdir "%PUBLISH_DIR%\dist"
copy /y "dist\%SOURCE_NAME%" "%PUBLISH_DIR%\dist\%SOURCE_NAME%" >nul
copy /y "dist\%BASE_NAME%.min.js" "%PUBLISH_DIR%\dist\%BASE_NAME%.min.js" >nul

pushd "%PUBLISH_DIR%"
git add -A dist
git diff --cached --quiet
if errorlevel 1 (
  git commit -m "chore: publish dist for %BASE_NAME%"
  if errorlevel 1 (
    popd
    goto :cleanup
  )
) else (
  echo No CDN changes to commit.
)

git push "%TARGET_REMOTE%" HEAD:"%TARGET_BRANCH%" --force
if errorlevel 1 (
  popd
  goto :cleanup
)
popd

echo.
echo Published successfully.
if defined REPO_SLUG (
  echo jsDelivr: https://cdn.jsdelivr.net/gh/%REPO_SLUG%@%TARGET_BRANCH%/dist/%BASE_NAME%.min.js
) else (
  echo CDN branch updated: %TARGET_REMOTE%/%TARGET_BRANCH%
)
goto :cleanup_success

:cleanup
echo ERROR: Publishing failed.
git worktree remove "%PUBLISH_DIR%" --force >nul 2>&1
exit /b 1

:cleanup_success
git worktree remove "%PUBLISH_DIR%" --force >nul 2>&1
exit /b 0