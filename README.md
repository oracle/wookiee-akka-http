# wookiee-akka-http
Component for Extending Wookiee Commands to Function as Akka Http Endpoints

[![Build Status](https://travis-ci.org/oracle/wookiee-akka-http.svg?branch=master)](https://travis-ci.org/oracle/wookiee-akka-http?branch=master) [![Latest Release](https://img.shields.io/github/release/oracle/wookiee-akka-http.svg)](https://github.com/oracle/wookiee-akka-http/releases) [![License](http://img.shields.io/:license-Apache%202-red.svg)](http://www.apache.org/licenses/LICENSE-2.0.txt)

[Main Wookiee Project](https://github.com/oracle/wookiee)

### Adding to Pom

Add the jfrog repo to your project first:
~~~~
<repositories>
    <repository>
        <id>JFrog</id>
        <url>http://oss.jfrog.org/oss-release-local</url>
    </repository>
</repositories>
~~~~

Add [latest version](https://github.com/oracle/wookiee-akka-http/releases/latest) of wookiee:
~~~~
<dependency>
    <groupId>com.webtrends</groupId>
    <artifactId>wookiee-akka-http_2.11</artifactId>
    <version>${wookiee.version}</version>
</dependency>
~~~~

### Disabling Access Logging
By default, we will log information about each http call to Wookiee Akka Http. To disable this,
add to your wookiee-akka-http config section the following:
~~~~
wookiee-akka-http {
  ...
  access-logging {
    enabled = false
  }
}
~~~~