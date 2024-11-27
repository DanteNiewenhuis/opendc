---
description: How to install OpenDC locally, and start experimenting in no time.
---

# Installation

This page describes how to set up and configure a local single-user OpenDC installation so that you can quickly get your
experiments running. You can also use the [hosted version of OpenDC](https://app.opendc.org) to get started even
quicker (The web server is however missing some more complex features).


## Prerequisites

1. **Supported Platforms**  
   OpenDC is actively tested on Windows, macOS and GNU/Linux.
2. **Required Software**  
   - A Java installation of version 19 or higher is required to run OpenDC. You may download the
   [Java distribution from Oracle](https://www.oracle.com/java/technologies/downloads/) or use the distribution provided
   by your package manager.
   - OpenDC exports all files using the parquet format. This means that for any type of analysis 
   a tool that can process parquet is needed. For this purpose, we advise using Python in combination with 
   Pandas and Matplotlib. This is also what we use in all our example tutorials / demos.

## Download

To get an OpenDC distribution, download a recent version from our [Releases](https://github.com/atlarge-research/opendc/releases) page on GitHub.
For basic usage, the OpenDCExperimentRunner is all that is needed.

## Setup

Unpack the downloaded OpenDC distribution. Opening OpenDCExperimentRunner results in two folders, `bin` and `lib`. 
`lib` contains all `.jar` files needed to run OpenDC. `bin` two executable versions of the OpenDCExperimentRunner. 
In the following pages, we discuss how to run an experiment using the executables.

# Building

OpenDC can also be build easily from a local build. This is done by assembling a distribution of the OpenDCExperimentRunner
using gradle. 

