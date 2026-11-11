#!/bin/bash
echo "Обновление кода из Git..."
git pull origin master
echo "Сборка JAR (Maven)..."
mvn clean package -DskipTests
echo "Копирование JAR..."
cp target/*.jar /srv/sklad/sklad-app.jar
echo "Перезапуск сервиса..."
sudo systemctl restart sklad
echo "Готово! Проверяем статус:"
sudo systemctl status sklad --no-pager
