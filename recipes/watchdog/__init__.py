from pythonforandroid.recipe import CythonRecipe

class WatchdogRecipe(CythonRecipe):
    version = '0.8.3'
    url = 'https://github.com/gorakhargosh/watchdog/archive/v0.8.3.zip'
    name = 'watchdog'

    depends = []

recipe = WatchdogRecipe()