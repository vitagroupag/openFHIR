
![openFHIR logo](https://open-fhir.com/images/openFIHR-logo-color-dark-horizontal-1.svg)

openFHIR is an engine that implements [FHIR Connect specification](https://sevkohler.github.io/FHIRconnect-spec/build/site/FHIRconnect/latest/index.html) and facilitates bidirectional mappings between openEHR and FHIR.
It is an open source edition of the commercial offering available at https://www.open-fhir.com

## [Documentation](https://open-fhir.com/documentation/index.html)
## [Sandbox](https://sandbox.open-fhir.com/)

## Build
### With docker and docker-compose

Build the docker image
```
docker-compose build
```

Run all services
```
docker-compose up
```

### Build with Maven
Build the jar
```
mvn clean package
```

Setup postgres (skip if postgres is locally configured)
```
docker-compose up db
```

Run using
```
java -jar target/*.jar
```

