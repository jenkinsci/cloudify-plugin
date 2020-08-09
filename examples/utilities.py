import argparse
import json
import os
import subprocess

def _run(jar_path, jenkins_url, args, **kwargs):
    full_jar_path = os.path.abspath(os.path.expanduser(jar_path))
    cmdline = ['java', '-jar', full_jar_path, '-s', jenkins_url]
    cmdline.extend(args)
    print("Running: %s" % cmdline)
    subprocess.run(
        cmdline, check=True, **kwargs)

def get_examples(definitions_file, jar_path, jenkins_url, **kwargs):
    with open(definitions_file, 'r') as def_f:
        definitions = json.load(def_f)

    os.makedirs('jobs', exist_ok=True)
    os.makedirs('views', exist_ok=True)
    for definition in definitions['definitions']:
        print("Processing definition: %s" % definition)
        with open(os.path.join('jobs', '%s.xml' % definition), 'w') as output_file:
            _run(jar_path, jenkins_url, ['get-job', definition], stdout=output_file)
    for view in definitions['views']:
        print("Processing view: %s" % view)
        with open(os.path.join('views', '%s.xml' % view), 'w') as output_file:
            _run(jar_path, jenkins_url, ['get-view', view], stdout=output_file)

def add_examples(definitions_file, jar_path, jenkins_url, **kwargs):
    with open(definitions_file, 'r') as def_f:
        definitions = json.load(def_f)
        
    for definition in definitions['definitions']:
        print("Uploading: %s" % (definition))
        with open(os.path.join('jobs', '%s.xml' % definition), 'r') as input_file:
            _run(jar_path, jenkins_url, ['create-job', definition], stdin=input_file)
    for view in definitions['views']:
        print("Uploading view: %s" % view)
        with open(os.path.join('views', '%s.xml' % view), 'r') as input_file:
            _run(jar_path, jenkins_url, ['create-view', view], stdin=input_file)

def install_plugins(definitions_file, jar_path, jenkins_url, **kwargs):
    with open(definitions_file, 'r') as def_f:
        definitions = json.load(def_f)
    
    for plugin_def in definitions['plugins']:
        print("Installing plugin: %s" % plugin_def)
        cmdline = ['install-plugin', plugin_def, '-deploy']
        if plugin_def == definitions['plugins'][-1]:
            cmdline.append('-restart')
        _run(jar_path, jenkins_url, cmdline)

def delete_builds(definitions_file, jar_path, jenkins_url, **kwargs):
    with open(definitions_file, 'r') as def_f:
        definitions = json.load(def_f)
    
    for definition in definitions['definitions']:
        print("Deleting builds for: %s" % (definition))
        _run(['delete-builds', definition, '1-1000'])


if __name__ == '__main__':
    common_parser = argparse.ArgumentParser(add_help=False)
    common_parser.add_argument('jenkins_url')
    common_parser.add_argument('jar_path')
    
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers()
    get_examples_subparser = subparsers.add_parser('get-examples', parents=[common_parser])
    get_examples_subparser.add_argument('definitions_file')
    get_examples_subparser.set_defaults(func=get_examples)
    add_examples_subparser = subparsers.add_parser('add-examples', parents=[common_parser])
    add_examples_subparser.add_argument('definitions_file')
    add_examples_subparser.set_defaults(func=add_examples)
    install_plugins_subparser = subparsers.add_parser('install-plugins', parents=[common_parser])
    install_plugins_subparser.add_argument('definitions_file')
    install_plugins_subparser.set_defaults(func=install_plugins)
    delete_builds_subparser = subparsers.add_parser('delete-builds', parents=[common_parser])
    delete_builds_subparser.add_argument('definitions_file')
    delete_builds_subparser.set_defaults(func=delete_builds)
    args = parser.parse_args()
    args.func(**vars(args))
