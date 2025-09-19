#!/bin/sh

java -cp ".:smscale.jar:javapos-1.14.2.jar:log4j-api-2.25.1.jar:log4j-core-2.25.1.jar:nrjavaserial-3.9.3.jar:xercesImpl-2.12.2.jar" -Djava.library.path=. com.bsmart.ScaleTests
#!java -cp ".:smscale.jar:javapos-1.14.2.jar:log4j-api-2.25.1.jar:log4j-core-2.25.1.jar:nrjavaserial-3.9.3.jar:xercesImpl-2.12.2.jar" -Djava.library.path=. com.bsmart.scaletst.ScaleTest
#!java -cp ".:smscale.jar:javapos-1.14.2.jar:log4j-api-2.25.1.jar:log4j-core-2.25.1.jar:nrjavaserial-3.9.3.jar:xercesImpl-2.12.2.jar" -Djava.library.path=. com.bsmart.scalecalib.MainDialog
#!java -cp ".:smscale.jar:javapos-1.14.2.jar:log4j-api-2.25.1.jar:log4j-core-2.25.1.jar:nrjavaserial-3.9.3.jar:xercesImpl-2.12.2.jar" -Djava.library.path=. com.bsmart.test.ConsoleTest

