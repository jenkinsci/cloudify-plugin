import argparse
import json
import os
import subprocess

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('mode', choices=['get', 'put'])
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
    
    if mode == 'get':
        os.makedirs('jobs', exist_ok=True)
        os.makedirs('views', exist_ok=True)
        for definition in definitions['definitions']:
            with open(os.path.join('jobs', '%s.xml' % definition), 'w') as output_file:
                subprocess.run(
                    ['java', '-jar', full_jar_path, '-s', jenkins_url, 'get-job', definition],
                    stdout=output_file)
        for view in definitions['views']:
            with open(os.path.join('views', '%s.xml' % view), 'w') as output_file:
                subprocess.run(
                    ['java', '-jar', full_jar_path, '-s', jenkins_url, 'get-view', view],
                    stdout=output_file)
            
    elif mode == 'put':
        for definition in definitions['definitions']:
            print("Uploading: %s" % (definition))
            with open(os.path.join('jobs', '%s.xml' % definition), 'r') as input_file:
                subprocess.run(
                    ['java', '-jar', full_jar_path, '-s', jenkins_url, 'create-job', definition],
                    stdin=input_file)
        for view in definitions['views']:
            with open(os.path.join('views', '%s.xml' % view), 'r') as input_file:
                subprocess.run(
                    ['java', '-jar', full_jar_path, '-s', jenkins_url, 'update-view', view],
                    stdin=input_file)
