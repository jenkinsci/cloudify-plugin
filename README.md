# Jenkins Plugin for Cloudify

This modules provides tight integration between Jenkins and Cloudify, simplifying using
Cloudify Manager through Jenkins jobs and pipelines.

## Prerequisites

There are no prerequisites for this plugin.

## Installation

At the moment, this plugin is not available through Jenkins' official plugins repository.
To install the plugin, download the HPI file from the "releases" section and install it
via Jenkins' "Advanced" panel in the "Manage Plugins" section.

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

## Available Build Steps

