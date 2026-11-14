@echo off
setlocal
title RESTROVISION - Live Camera AI Ingestion Worker
echo ======================================================================
echo          RESTROVISION EDGE AI - LIVE CAMERA INGESTION WORKER
echo ======================================================================
echo.
echo Select Camera Source:
echo   [1] Default Webcam / Laptop Camera (Source 0)
echo   [2] External USB Camera (Source 1)
echo   [3] RTSP IP Camera (Enter stream URL)
echo   [4] Video File (.mp4)
echo   [5] Simulation Mode (Virtual patron occupancy test)
echo   [6] Run Edge AI Verification Tests
echo.
set /p choice="Enter choice [1-6] (default=1): "

set PYTHON_EXE=C:\app\Admin\product\26ai\dbhomeFree\python\bin\python.exe

if "%choice%"=="" set choice=1
if "%choice%"=="1" (
    "%PYTHON_EXE%" "D:\RESTROVISION\edge-ai\app\camera\camera_worker.py" 0
) else if "%choice%"=="2" (
    "%PYTHON_EXE%" "D:\RESTROVISION\edge-ai\app\camera\camera_worker.py" 1
) else if "%choice%"=="3" (
    set /p rtsp_url="Enter RTSP URL (e.g., rtsp://admin:pass@192.168.1.100:554/stream1): "
    "%PYTHON_EXE%" "D:\RESTROVISION\edge-ai\app\camera\camera_worker.py" "%rtsp_url%"
) else if "%choice%"=="4" (
    set /p vid_path="Enter path to MP4 video file: "
    "%PYTHON_EXE%" "D:\RESTROVISION\edge-ai\app\camera\camera_worker.py" "%vid_path%"
) else if "%choice%"=="5" (
    "%PYTHON_EXE%" "D:\RESTROVISION\edge-ai\app\camera\camera_worker.py" sim
) else if "%choice%"=="6" (
    "%PYTHON_EXE%" "D:\RESTROVISION\edge-ai\run_tests.py"
) else (
    echo Invalid option selected.
)

pause
