# Smart Meter eXtension Core Software (SMXcore)
Open Source software for communication with electricity and other meter types developed as part of the [Nobel Grid](http://nobelgrid.eu/) project and improved in various other projects.

## Requirements

* Java SE 7 (?)
* gurux.serial 1.16
* gurux.dlms 1.2.31
* gurux.net 1.0.9
* gurux.sms 1.0.6
* gurux.serial 1.0.16
* gurux.terminal 1.0.6
* jssc 2.8.0
* mongo-java-driver 3.2.2
* mqtt-client 0.4.0
* pi4j-core.jar 1.1
* pi4j-gpio-extension.jar 1.1
* javax-json 1.1
* javax-json-api 1.1

## Installation
Netbeans is currently required. Create a new project in Netbeans by importing the root of the project directory. A build will produce a `dist/` directory which can be distributed. 

## Running

Change working directory to the `dist/` directory created above.

SMXcore can then be run on the command line using 

`java -jar $PATH_TO_SMXCORE/SMXcore.jar Modules.txt`

where `Modules.txt` is a descriptor of modules to load (see below). Additional files describing the module configuration should be placed in the same folder as `Modules.txt`.

## Configuration

Example configuration for different applications (App) can be found in `src/conf`. 
