# Polypheny-DB Control #
Polypheny-DB Control allows to easily deploy and monitor Polypheny-DB. 

## Getting Started ##
This section describes how to setup Polypheny-DB Control to build and run Polypheny-DB.

## Requirements ##
To build and start Polypheny-DB using Polypheny-DB Control you need to have Java JDK in Version 8 or higher installed on your system.
Thanks to [JGit](https://github.com/eclipse/jgit), Polypheny-DB Control contains a pure Java implementation of Git. Therefore, it is no longer required to have Git installed on the system.

### Setup ###
Download the latest [polyphenydb-control.jar](https://github.com/polypheny-db/Polypheny-DB-Control/releases/latest) from the release section. 
To start the Web-UI execute `polyphenydb-control.jar` by specifying the parameter `control`.

```
java -jar polyphenydb-control.jar control
```

The interface can now be accessed on port 8070. This port can be changed using the parameter `-p`:

```
java -jar polyphenydb-control.jar control -p 8070
```

We recommend not to use port 8080, 8081 and 8082 because these are the default ports of services offered by Polypheny-DB.

## Roadmap ##
See the [open issues](https://github.com/polypheny-db/Polypheny-DB-Control/issues) for a list of proposed features (and known issues).

## License ##
The MIT License (MIT)

