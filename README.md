# Jenkins Plugin for Cloudify

This modules provides tight integration between Jenkins and Cloudify, simplifying using
Cloudify Manager through Jenkins jobs and pipelines.

* [Prerequisites](#prerequisites)
* [Terminology](#terminology)
    * [Deployment Outputs File](#deployment-outputs-file)
    * [Inputs Mapping File](#inputs-mapping-file)
* [Installation](#installation)
* [Configuration](#configuration)
* [Parameter Types](#parameter-types)
    * [Cloudify Blueprint Selector](#cloudify-blueprint-selector)
    * [Cloudify Deployment Selector](#cloudify-deployment-selector)
    * [Cloudify Constrained Input Value Selector](#cloudify-constrained-input-value-selector)
* [Available Build Steps](#available-build-steps)
    * [Upload Cloudify Plugin](#upload-cloudify-plugin)
    * [Upload Cloudify Blueprint](#upload-cloudify-blueprint)
    * [Delete Cloudify Blueprint](#delete-cloudify-blueprint)
    * [Build Cloudify Environment](#build-cloudify-environment)
    * [Delete Cloudify Environment](#delete-cloudify-environment)
    * [Execute Cloudify Workflow](#execute-cloudify-workflow)
    * [Convert Cloudify Environment Outputs/Capabilities to Inputs](#convert-cloudify-environment-outputs-capabilities-to-inputs)
* [Cloudify's Build Wrapper](#cloudify-s-build-wrapper)
* [Planned Improvements](#planned-improvements)

## Prerequisites

There are no prerequisites for this plugin.

## Terminology

### Deployment Outputs File

Certain build steps (such as the "Create Environment" build step, or the "Cloudify" wrapper) allow
you to write a "Deployment Outputs File" at the end of creating the environment. This file can be
used by subsequent build steps, to gather information about the environment that had just been
created.

The file is a JSON file, which adheres to the following format:

```
{
    "outputs": {
        "output_1_name": output_1_value,
        "output_2_name": output_2_value,
        ...
    },
    "capabilities": {
        "cap_1_name": cap_1_value,
        "cap_2_name": cap_2_value,
        ...
    },
}
```

For example:

```json
{
    "outputs": {
        "endpoint": "10.0.0.131",
        "auth_info": {
            "username": "admin",
            "password": "very_secret"
        }
    },
    "capabilities": {}
}
```

The example above shows two outputs (one of them is a dictionary), and no capabilities.

### Inputs Mapping File

Often, the outputs of a deployment (see "Deployment Outputs File" above) are used, in whole or in part, as inputs
to subsequent Cloudify operations. This transformation can be accomplished in various Cloudify build-steps
by providing an *inputs mapping file*, which is a YAML/JSON file that provides mapping information.

The structure of an inputs mapping file is as follows:

```
{
    "outputs": {
        "output_1_name": "input_1_name",
        "output_2_name": "input_2_name",
        ...
    },
    "capabilities": {
        "cap_1_name": "input_3_name",
        "cap_2_name": "input_4_name",
        ...
    }
}
```

For example, considering the outputs file above, and the following mapping file:

```json
{
    "outputs": {
        "endpoint": "endpoint_ip",
        "auth_info": "user_info"
    }
}
```

The resultant JSON file will look like this:

```json
{
    "endpoint_ip": "10.0.0.131",
    "user_info": {
        "username": "admin",
        "password": "very_secret"
    }
}
```

## Installation

At the moment, this plugin is not available through Jenkins' official plugins repository.
To install the plugin, download the HPI file from the "releases" section and install it
via Jenkins' "Advanced" panel in the "Manage Plugins" section.

## Configuration

You should define the Cloudify Manager endpoint and credentials in Jenkins' console ("Manage Jenkins" ->
"Configure System", look for "Cloudify Manager").

## Parameter Types

Jenkins provides the capability of parameterized builds. Users can configure jobs and pipelines to
accept parameters prior to execution.

Cloudify's plugin for Jenkins provides a few parameter types, which can simplify the definition of
jobs and pipelines.

The following parameter types are available:

### Cloudify Blueprint Selector

During job definition, the job admin provides a parameter name (in the "Name" field),
as well as an optional filter.

When running the job with parameters, the user will be shown a dropdown box, containing a list of
blueprints from Cloudify Manager. If a filter was provided, then the list will only contain
blueprints with ID's containing the filter string.

### Cloudify Deployment Selector

Similar to the Blueprint Selector, however this parameter type displays a dropdown box containing
existing deployment ID's.

### Cloudify Constrained Input Value Selector

Given (during job definition) a blueprint ID and an input name, this parameter type
will render a dropdown box containing all allowed values for the input.

For this to work, the input must be defined in the blueprint so it has exactly
one constraint of type `valid_values` (it may have other constraints, but exactly one of them
must be of type `valid_values`).

## Available Build Steps

### Upload Cloudify Plugin

Use this build-step to upload a plugin to Cloudify Manager.

### Upload Cloudify Blueprint

This build-step uploads a blueprint to Cloudify Manager. The blueprint source may be provided as either:

* Path to a directory on the local filesystem
* Path to an archive on the local filesystem
* A URL to a `tar.gz` file

In addition, the blueprint's main YAML file must be provided.

### Delete Cloudify Blueprint

Use this build-step to delete a blueprint from Cloudify Manager by its ID.

### Build Cloudify Environment

Use this build-step to create a Cloudify deployment.

### Delete Cloudify Environment

Use this build-step to delete a Cloudify deployment.

### Execute Cloudify Workflow

Use this build-step to execute a workflow on a deployment.

### Convert Cloudify Environment Outputs/Capabilities to Inputs

Use this build-step to transform a Deployment Outputs File to a standard Deployment Inputs File
(see "Inputs Mapping File" above).

## Cloudify's Build Wrapper

The Cloudify Plugin for Jenkins also provides a Jenkins *Build Wrapper*.

## Planned Improvements

* Supporting multiple Cloudify Manager installations
* Propagating Jenkins authentication info to Cloudify