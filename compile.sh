mvn clean install

echo "Deletes Framework jar from Test project..."
rm -f "../Test/libs/Framework-*.jar"

echo "Copying framework jar to Test project..."
cp -rf target/Framework-*.jar ../Test/libs/


