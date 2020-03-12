import argparse
import json
import os
import subprocess

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('file')
    parser.add_argument('url')
    parser.add_argument('jar_path')
    args = parser.parse_args()
    
    def_file = args.file
    jenkins_url = args.url
    jar_path = args.jar_path
    
    with open(def_file, 'r') as def_f:
        definitions = json.load(def_f)
    
    full_jar_path = os.path.abspath(os.path.expanduser(jar_path))
    os.makedirs('jobs', exist_ok=True)
    for definition in definitions['definitions']:
        with open(os.path.join('jobs', '%s.xml' % definition), 'w') as output_file:
            subprocess.run(
                ['java', '-jar', full_jar_path, '-s', jenkins_url, 'get-job', definition],
                stdout=output_file)
