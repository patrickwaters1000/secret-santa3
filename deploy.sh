sudo apt update
sudo apt upgrade -y
sudo apt install default-jre
sudo apt install default-jdk

HOST=ec2-3-137-186-45.us-east-2.compute.amazonaws.com
JAR_NAME=secret-santa3-0.1.0-SNAPSHOT-standalone.jar
scp -i "secret-santa-key-pair.pem" \
    back/target/uberjar/$JAR_NAME \
    ubuntu@$HOST:~/app.jar
