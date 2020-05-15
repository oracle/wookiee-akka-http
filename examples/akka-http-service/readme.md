# Wookiee Examples - Akka Http Service
A one line Service!! This is an example of how easy it is to get your project running
on Wookiee. This one line service can be run just like it would be on a server.

## Building
First build parent wookiee project by navigating to ../wookiee-akka-http and running
> mvn clean install

This will get the SNAPSHOT of Wookiee-Akka-Http into your local repo.

Then build Basic Service and its tests with the same command in ../examples/akka-http-service
> mvn clean install 

## Running Service Locally
If you want to see what it looks like to have a Wookiee service running, then open
this repo up in IntelliJ Idea and do the following...

* Right click on pom.xml in ../examples/akka-http-service and click "Add as Maven Project"
    * This adds the example repo and its code as a module to your project
* Edit Run/Debug Configurations
* Hit the "+" and select Application
* Input the Following:
    * Main Class: com.webtrends.harness.app.HarnessService
    * VM Options: -Dconfig.file=src/main/resources/application.conf -Dlogback.configurationFile=src/main/resources/logback.xml
    * Working Directory: ${your path to ../wookiee-akka-http/examples/akka-http-service}
    * Use Classpath of Module: akka-http-service
* Press "OK"

You can then take advantage of Wookiee's health checks:
* [Full Healthchecks](http://localhost:8080/healthcheck)
    * [Load Balancer Healthchecks](http://localhost:8080/healthcheck/lb)
    * [Nagios Healthchecks](http://localhost:8080/healthcheck/nagios)
