@echo off
chcp 65001
set JAVA_OPTS=-Dfile.encoding=UTF-8 -Dconsole.encoding=UTF-8
mvn spring-boot:run
