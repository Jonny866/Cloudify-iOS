@echo off
chcp 65001 >nul
echo ========================================================
echo   Cloudify - Сохранение базы данных на диск D
echo ========================================================
echo.

set BACKUP_DIR=D:\Cloudify\database_backup
if not exist "%BACKUP_DIR%" (
    mkdir "%BACKUP_DIR%"
)

echo [1/3] Поиск подключенного Android устройства или эмулятора...
where adb >nul 2>nul
if %errorlevel% neq 0 (
    echo [!] ADB не найден в системном PATH.
    echo Поиск ADB в Android SDK...
    if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" (
        set "ADB_PATH=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
    ) else (
        echo.
        echo [Справка]: Если телефон подключен по USB к компьютеру:
        echo 1. Откройте на компьютере Проводник -^> Ваш телефон -^> Загрузки (Download) -^> Cloudify_Backups
        echo 2. Скопируйте файл cloudify_backup_latest.json прямо в папку %BACKUP_DIR%
        echo.
        pause
        exit /b 0
    )
) else (
    set "ADB_PATH=adb"
)

echo [2/3] Копирование JSON резервной копии плейлистов и треков...
"%ADB_PATH%" pull /sdcard/Download/Cloudify_Backups/cloudify_backup_latest.json "%BACKUP_DIR%\cloudify_backup_latest.json" >nul 2>nul
if %errorlevel% equ 0 (
    echo [+] Файл cloudify_backup_latest.json успешно сохранен в %BACKUP_DIR%!
) else (
    echo [i] Копирование через системное хранилище приложения...
    "%ADB_PATH%" exec-out run-as com.cloudify.music cat files/cloudify_backup_latest.json > "%BACKUP_DIR%\cloudify_backup_latest.json" 2>nul
)

echo [3/3] Копирование сырой базы данных SQLite (cloudify_music.db)...
"%ADB_PATH%" exec-out run-as com.cloudify.music cat databases/cloudify_music.db > "%BACKUP_DIR%\cloudify_music.db" 2>nul

if exist "%BACKUP_DIR%\cloudify_music.db" (
    echo [+] Сырая база данных cloudify_music.db сохранена в %BACKUP_DIR%!
)

echo.
echo ========================================================
echo База данных Cloudify успешно сохранена на диске D:
echo Папка: %BACKUP_DIR%
echo ========================================================
echo.
pause
