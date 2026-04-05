import re

with open("CityNetTVProvider/src/main/kotlin/com/citynettv/CityNetTVApi.kt", "r") as f:
    text = f.read()

pattern = re.compile(r'<<<<<<< Updated upstream.*?=======\n(.*?)\n>>>>>>> Stashed changes', re.DOTALL)
resolved = pattern.sub(r'\1', text)

with open("CityNetTVProvider/src/main/kotlin/com/citynettv/CityNetTVApi.kt", "w") as f:
    f.write(resolved)
