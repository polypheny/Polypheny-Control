# Polypheny Control
_Polypheny Control_ allows to easily deploy and monitor Polypheny-DB. 


## Getting Started
This section describes how to setup Polypheny Control to build and run Polypheny-DB.


### Requirements
To build and start Polypheny-DB using Polypheny Control you need to have Java JDK in Version 8 or higher installed on your system.
Thanks to [JGit](https://github.com/eclipse/jgit), Polypheny Control contains a pure Java implementation of Git. Therefore, it is no longer required to have Git installed on the system.


### Setup
Download the latest [polypheny-control.jar](https://github.com/polypheny/Polypheny-Control/releases/latest) from the release section. 
To start the Web-UI execute `polypheny-control.jar` by specifying the parameter `control`.

```
java -jar polypheny-control.jar control
```

The interface can now be accessed on port 8070. This port can be changed using the parameter `-p`:

```
java -jar polypheny-control.jar control -p 8070
```

We strongly recommend not to use port 8080, 8081 and 8082 because these are the default ports of services offered by Polypheny-DB.


## Roadmap
See the [open issues](https://github.com/polypheny/Polypheny-Control/issues) for a list of proposed features (and known issues).


## Contributing
We highly welcome your contributions to _Polypheny Control_. If you would like to contribute, please fork the repository and submit your changes as a pull request. Please consult our [Admin Repository](https://github.com/polypheny/Admin) for guidelines and additional information.

Please note that we have a [code of conduct](https://github.com/polypheny/Admin/blob/master/CODE_OF_CONDUCT.md). Please follow it in all your interactions with the project. 


## Credits
_Polypheny Control_ builds upon the great work of several other open source projects:

#### Frontend
* [ansi_up.js](https://github.com/drudru/ansi_up): Converting text with ANSI terminal codes into colorful HTML.
* [Font Awesome](https://fontawesome.com/): A set of web-related icons.
* [jQuery](https://jquery.com/): The library that makes Javascript usable.
* [jquery.serializeJSON](https://github.com/marioizquierdo/jquery.serializeJSON): Serialize an HTML Form to a JavaScript Object.
* [Tooltipster](https://iamceege.github.io/tooltipster/): jQuery plugin for modern tooltips.

All those libraries are imported using [WebJars](https://www.webjars.org/).


#### Backend
* [Airline](https://rvesse.github.io/airline/): Annotation-driven Java library for building command line interfaces.
* [Apache Commons](http://commons.apache.org/): A bunch of useful Java utility classes.
* [GSON](https://github.com/google/gson): Convert Java Objects into their JSON representation and vice versa.
* [Javalin](https://javalin.io/): A simple and lightweight java web framework.
* [JGit](https://www.eclipse.org/jgit/): Pure Java implementation of the Git version control system.
* [Log4j](https://logging.apache.org/log4j/2.x/): Fast and flexible logging framework for Java.
* [Project Lombok](https://projectlombok.org/): A library providing compiler annotations for tedious tasks.
* [SLF4J](http://www.slf4j.org/): Provides a logging API by means of a facade pattern.
* [Typesafe Config](https://lightbend.github.io/config/): A configuration library using HOCON files.
* [WinP](http://winp.kohsuke.org/): Windows process management library.

Those projects are used "as is" and are integrated as libraries.


## License
The MIT License (MIT)
