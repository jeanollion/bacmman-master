
[![Release](https://jitpack.io/v/jeanollion/bacmman.svg)](https://jitpack.io/jeanollion/bacmman)

BACteria in Mother Maching Analyzer (BACMMAN) is a software allowing fast and reliable automated image analysis of high-throughput 2D/3D time-series images from mother machine. Mother machine is a very popular microfluidic device allowing investigating biological processes in bacteria at the single-cell level.

# Application

BACMMAN can process this kind of data:

| Fluorescence Images | Phase-contrast Images |
| :---:         |          :---: |
| <img src="https://github.com/jeanollion/bacmman/wiki/resources/fluo.gif" width="400"> | <img src="https://github.com/jeanollion/bacmman/wiki/resources/phase.gif" width="600">    |

See the [wiki](https://github.com/jeanollion/bacmman/wiki) for detailed information

# Publications
- Example datasets are taken from: [Robert et al. Mutation dynamics and fitness effects followed in single cells, Science 2018](http://science.sciencemag.org/content/359/6381/1283)
- When using this work please cite [J. Ollion, L. Robert, and M. Elez, “High-throughput detection and tracking of cells and intracellular spots in mother machine experiments” Nature Protocols, 2019.](https://rdcu.be/bRSze)

# Licence

BACMMAN is a free/libre open source software under GNU General Public License v3.0 ([see license](https://github.com/jeanollion/bacmman/blob/master/LICENSE.txt)) 

# Structure of repository
BACMMAN is composed of several modules:
- The core module on which depend all other modules
- The graphical user interface
- Mother machine modules: image processing modules specifically designed for analysis of mother machine experiments
- IJ1 binding integration in FIJI environment
- Headless for run in command line mode 
