# Smart Meter eXtension Core Software (SMXcore)
Open Source software for communication with electricity and other meter types developed as part of the [Nobel Grid](http://nobelgrid.eu/) project and improved in various other projects.

## Requirements

* Java 8 CE (?)

## Installation
Netbeans is currently required. Create a new project in Netbeans by importing the root of the project directory. A build will produce a dist/ directory which can be distributed. 

## Running

Change working directory to the dist/ directory created above.

SMXcore can then be run on the command line using 

`java -jar $PATH_TO_SMXCORE/SMXcore.jar Modules.txt`

where Modules.txt is a descriptor of modules to load (see below). Additional files describing the module configuration should be placed in the same folder as `Modules.txt`.

## Configuration

Example configuration for different applications (App) can be found in src/conf. 
