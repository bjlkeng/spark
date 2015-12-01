#!/bin/bash

#./make-distribution.sh --tgz --skip-java-test -Pyarn -Phadoop-2.4 -Dhadoop.version=2.4.0
#./make-distribution.sh --tgz --skip-java-test -Pyarn -Phadoop-2.4 -Phadoop-aws -Dhadoop.version=2.6.0
./make-distribution.sh --tgz --skip-java-test -Pyarn -Phadoop-2.6 -Phadoop-aws -Dhadoop.version=2.7.0

#mvn clean package -Pyarn -Dyarn.version=2.6.0 -Phadoop-2.4 -Dhadoop.version=2.6.0 -Phive -DskipTests

#mvn -Pyarn -Phadoop-2.4 -Dhadoop.version=2.4.0 -DskipTests package
#mvn -Pyarn -Phadoop-2.4 -Dhadoop.version=2.4.0 -DskipTests test

#mvn -Pyarn -Phadoop-2.4 -Dhadoop.version=2.4.0 -pl mllib -Dtest=*KMeans* -Dsuites=*KMeans* test
#mvn -Pyarn -Phadoop-2.4 -Dhadoop.version=2.4.0 clean test
