import argparse
import json
import os
import subprocess

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['get', 'put', 'delete-builds'])
    parser.add_argument('file')
    parser.add_argument('url')
    parser.add_argument('jar_path')
    args = parser.parse_args()
    
    mode = args.mode
    def_file = args.file
    jenkins_url = args.url
    jar_path = args.jar_path
    
    with open(def_file, 'r') as def_f:
        definitions = json.load(def_f)
    
    full_jar_path = os.path.abspath(os.path.expanduser(jar_path))
    
    if not os.path.isfile(full_jar_path):
        raise Exception("Jar not found in %s" % full_jar_path)
    
    def _run(args, **kwargs):
        cmdline = ['java', '-jar', full_jar_path, '-s', jenkins_url]
        cmdline.extend(args)
        subprocess.run(
            cmdline, check=True, **kwargs)

    if mode == 'get':
        os.makedirs('jobs', exist_ok=True)
        os.makedirs('views', exist_ok=True)
        for definition in definitions['definitions']:
            print("Processing defintion: %s" % definition)
            with open(os.path.join('jobs', '%s.xml' % definition), 'w') as output_file:
                _run(['get-job', definition], stdout=output_file)
        for view in definitions['views']:
            print("Processing view: %s" % view)
            with open(os.path.join('views', '%s.xml' % view), 'w') as output_file:
                _run(['get-view', view], stdout=output_file)
            
    elif mode == 'put':
        for definition in definitions['definitions']:
            print("Uploading: %s" % (definition))
            with open(os.path.join('jobs', '%s.xml' % definition), 'r') as input_file:
                _run(['create-job', definition], stdin=input_file)
        for view in definitions['views']:
            print("Uploading view: %s" % view)
            with open(os.path.join('views', '%s.xml' % view), 'r') as input_file:
                _run(['create-view', view], stdin=input_file)

    elif mode == 'delete-builds':
        for definition in definitions['definitions']:
            print("Deleting builds for: %s" % (definition))
            _run(['delete-builds', definition, '1-1000'])
