@echo off
echo 正在启动套利机器人...
echo.

REM 设置Java编码
set JAVA_OPTS=-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8

REM 启动应用
java %JAVA_OPTS% -jar target\bot.arbitrage-1.0-SNAPSHOT.jar

echo.
echo 程序已退出
pause
