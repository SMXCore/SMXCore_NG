# Smart Meter eXtension Core Software (SMXcore)
Open Source software for communication with electricity and other meter
types developed as part of the [Nobel Grid](http://nobelgrid.eu/) project
and improved in various other projects.

## Requirements

Run:
* Java SE JRE 7/8 (?)

Build:
* Java SE JDK 7/8
* Maven

## Build and Installation
The build uses Maven.

To create a .zip of the jar file with dependencies in sub-folder for
deployment using Maven execute (Linux):

`mvn package`

The .zip can then be found in the target/ folder.

To install to the local repository:

`mvn install`

## Running (Standalone from zip)

Unzip the above created zip.

Example configuration can be found in `src/test/conf`. Copy one of
these folders or create your own based on them.

Change working directory to the a configuration folder.

SMXcore can then be run on the command line using:

`java -jar ../SMXcore.jar Modules.txt`

where `Modules.txt` is a descriptor of modules to load (see below).

Additional files describing the module configuration should be placed in
the same folder as `Modules.txt`.

## Configuration

Example configuration for different applications (App) can be found in `src/conf`.

## Develop

The source repository can be imported into Netbeans (or another IDE) as a Maven project.
