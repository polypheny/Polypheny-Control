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


## License
The MIT License (MIT)
