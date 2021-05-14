#!/usr/bin/env python3
import json
import os
import sys
import traceback

from dotenv import load_dotenv
from flask import Flask, request, jsonify
from flask_compress import Compress
from flask_cors import CORS

from opendc.util import rest, path_parser, database
from opendc.util.auth import AuthError, AuthManager, AsymmetricJwtAlgorithm
from opendc.util.exceptions import AuthorizationTokenError, RequestInitializationError
from opendc.util.json import JSONEncoder

load_dotenv()

TEST_MODE = "OPENDC_FLASK_TESTING" in os.environ

# Setup Sentry if DSN is specified
if 'SENTRY_DSN' in os.environ:
    import sentry_sdk
    from sentry_sdk.integrations.flask import FlaskIntegration

    sentry_sdk.init(
        integrations=[FlaskIntegration()],
        traces_sample_rate=0.1
    )

# Set up database if not testing
if not TEST_MODE:
    database.DB.initialize_database(
        user=os.environ['OPENDC_DB_USERNAME'],
        password=os.environ['OPENDC_DB_PASSWORD'],
        database=os.environ['OPENDC_DB'],
        host=os.environ.get('OPENDC_DB_HOST', 'localhost'))

# Set up the core app
app = Flask("opendc")
app.testing = TEST_MODE
app.config['SECRET_KEY'] = os.environ['OPENDC_FLASK_SECRET']
app.json_encoder = JSONEncoder

# Set up CORS support
CORS(app)

compress = Compress()
compress.init_app(app)

auth = AuthManager(AsymmetricJwtAlgorithm(jwks_url=f"https://{os.environ['AUTH0_DOMAIN']}/.well-known/jwks.json"),
                   issuer=f"https://{os.environ['AUTH0_DOMAIN']}/", audience=os.environ['AUTH0_AUDIENCE'])

API_VERSIONS = {'v2'}


@app.errorhandler(AuthError)
def handle_auth_error(ex):
    response = jsonify(ex.error)
    response.status_code = ex.status_code
    return response


@app.route('/<string:version>/<path:endpoint_path>', methods=['GET', 'POST', 'PUT', 'DELETE'])
@auth.require
def api_call(version, endpoint_path):
    """Call an API endpoint directly over HTTP."""

    # Check whether given version is valid
    if version not in API_VERSIONS:
        return jsonify(error='API version not found'), 404

    # Get path and parameters
    (path, path_parameters) = path_parser.parse(version, endpoint_path)

    query_parameters = request.args.to_dict()
    for param in query_parameters:
        try:
            query_parameters[param] = int(query_parameters[param])
        except:
            pass

    try:
        body_parameters = json.loads(request.get_data())
    except:
        body_parameters = {}

    # Create and call request
    (req, response) = _process_message({
        'id': 0,
        'method': request.method,
        'parameters': {
            'body': body_parameters,
            'path': path_parameters,
            'query': query_parameters
        },
        'path': path,
        'token': request.headers.get('auth-token')
    })

    print(
        f'HTTP:\t{req.method} to `/{req.path}` resulted in {response.status["code"]}: {response.status["description"]}')
    sys.stdout.flush()

    flask_response = jsonify(json.loads(response.to_JSON()))
    flask_response.status_code = response.status['code']
    return flask_response


def _process_message(message):
    """Process a request message and return the response."""

    try:
        req = rest.Request(message)
        res = req.process()

        return req, res

    except AuthorizationTokenError:
        res = rest.Response(401, 'Authorization error')
        res.id = message['id']

    except RequestInitializationError as e:
        res = rest.Response(400, str(e))
        res.id = message['id']

        if not 'method' in message:
            message['method'] = 'UNSPECIFIED'
        if not 'path' in message:
            message['path'] = 'UNSPECIFIED'

    except Exception:
        res = rest.Response(500, 'Internal server error')
        if 'id' in message:
            res.id = message['id']
        traceback.print_exc()

    req = rest.Request()
    req.method = message['method']
    req.path = message['path']

    return req, res


if __name__ == '__main__':
    app.run()
