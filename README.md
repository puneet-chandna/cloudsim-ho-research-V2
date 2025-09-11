# CloudSim-HO-Research-V2

![Build Status](https://img.shields.io/badge/build-passing-brightgreen)
![License](https://img.shields.io/badge/license-MIT-blue)

A research framework for evaluating the Hippopotamus Optimization (HO) algorithm for Virtual Machine (VM) placement in cloud data centers.

## Key Features

- A comprehensive implementation of the Hippopotamus Optimization (HO) algorithm.
- A robust platform for comparing the HO algorithm against baseline allocation strategies like FirstFit, BestFit, and Genetic Algorithm (GA).
- In-depth parameter sensitivity analysis and scalability testing.
- Detailed performance metrics, including resource utilization, SLA violations, and power consumption.

## Getting Started

### Prerequisites

- Java 21
- Maven 3.9+

### Installation

```bash
git clone https://github.com/puneet-chandna/cloudsim-ho-research-V2.git
cd cloudsim-ho-research-V2
mvn clean install
```

## Usage

To run the default experiment, which includes the Micro, Small, and Medium scenarios, run the following command:

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

## Documentation

For more detailed information, please see the full [documentation](https://cloudsim-ho-project.puneetchandna.com/).

## Contributing

We welcome contributions! Please see our [CONTRIBUTING.md](CONTRIBUTING.md) for more information.

## Code of Conduct

This project has a [Code of Conduct](CODE_OF_CONDUCT.md) that all contributors are expected to adhere to.

## License

This project is licensed under the [MIT License](./LICENSE).

## Acknowledgments

This project is built on top of [CloudSim Plus](http.cloudsimplus.org/), a modern and full-featured framework for modeling and simulating cloud computing environments.
