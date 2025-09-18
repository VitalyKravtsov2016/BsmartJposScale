#!/bin/sh

java -cp ".:jpos113.jar:log4j-1.2.15.jar:nrjavaserial-5.2.1.jar:xerces.jar:smscale.jar" -Djava.library.path=. com.bsmart.ScaleTests
#!java -cp .:jpos113.jar:log4j-1.2.15.jar:nrjavaserial-5.2.1.jar:xerces.jar:smscale.jar -Djava.library.path=. com.bsmart.scaletst.ScaleTest
#!java -cp .:jpos113.jar:log4j-1.2.15.jar:nrjavaserial-5.2.1.jar:xerces.jar:smscale.jar -Djava.library.path=. com.bsmart.scalecalib.MainDialog
#!java -cp .:jpos113.jar:log4j-1.2.15.jar:nrjavaserial-5.2.1.jar:xerces.jar:smscale.jar -Djava.library.path=. com.bsmart.test.ConsoleTest

