# Contributing to CloudSim HO Research

First off, thank you for considering contributing to this project! Your help is greatly appreciated.

## How to Report Bugs

If you find a bug, please open an issue on the GitHub repository. Please include the following information in your bug report:

*   A clear and concise description of the bug.
*   Steps to reproduce the bug.
*   The expected behavior.
*   The actual behavior.
*   The version of the project you are using.
*   Any relevant logs or screenshots.

## How to Suggest Enhancements

If you have an idea for an enhancement, please open an issue on the GitHub repository. Please include the following information in your enhancement suggestion:

*   A clear and concise description of the enhancement.
*   The problem that the enhancement solves.
*   Any alternative solutions or features you've considered.

## Your First Code Contribution

Unsure where to begin contributing? You can start by looking through the `good first issue` and `help wanted` issues.

## Pull Request Process

1.  Ensure any install or build dependencies are removed before the end of the layer when doing a build.
2.  Update the README.md with details of changes to the interface, this includes new environment variables, exposed ports, useful file locations and container parameters.
3.  Increase the version numbers in any examples files and the README.md to the new version that this Pull Request would represent. The versioning scheme we use is [SemVer](http://semver.org/).
4.  You may merge the Pull Request in once you have the sign-off of two other developers, or if you do not have permission to do that, you may request the second reviewer to merge it for you.

## How to Build

This project uses Maven to manage dependencies and build the project.

1.  **Prerequisites:**
    *   Java 21 or later
    *   Maven 3.9 or later

2.  **Build the project:**
    ```bash
    mvn clean install
    ```

## How to Run Tests

This project uses JUnit 5 for testing.

1.  **Run all tests:**
    ```bash
    mvn test
    ```

## How to Run the Application

To run the full experiment, use the following commands:

**PowerShell:**
```powershell
./run-experiment.ps1
```

**Bash:**
```bash
./run-experiment.sh
```

### Running the Simulation from JAR

To run the simulation from the JAR file, use the `run-simulation` scripts. Make sure the JAR file name in the script matches the one in your `target` directory.

**PowerShell:**
```powershell
./run-simulation.ps1
```

**Bash:**
```bash
./run-simulation.sh
```
