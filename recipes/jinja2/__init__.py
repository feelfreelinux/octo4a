
from pythonforandroid.recipe import PythonRecipe


class Jinja2Recipe(PythonRecipe):
    # The webserver of 'master' seems to fail
    # after a little while on Android, so use
    # 0.10.1 at least for now
    version = '2.8.1'
    url = 'https://github.com/pallets/jinja/archive/{version}.zip'

    depends = ['setuptools']

    python_depends = ['itsdangerous']

    call_hostpython_via_targetpython = False
    install_in_hostpython = False


recipe = Jinja2Recipe()
