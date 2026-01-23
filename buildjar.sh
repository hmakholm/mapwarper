#! /bin/sh
set -x
set -e
rm -f mapwarper.jar
cd src
find -name '*.class' -delete
find -name '*.java' | xargs javac
jar --create --file=../mapwarper.jar --main-class=net.makholm.henning.mapwarper.Mapwarper \
  `find * -name *.png -o -name *.class`
find -name '*.class' -delete
