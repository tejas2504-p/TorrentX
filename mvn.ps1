$MAVEN_HOME = "C:\Users\tejas\OneDrive\30BB~1\Projects\TorrentX\apache-maven-3.9.6"
$PROJECT_DIR = "C:\Users\tejas\OneDrive\30BB~1\Projects\TorrentX"
java -classpath "$MAVEN_HOME/boot/plexus-classworlds-2.7.0.jar" "-Dclassworlds.conf=$MAVEN_HOME/bin/m2.conf" "-Dmaven.home=$MAVEN_HOME" "-Dmaven.multiModuleProjectDirectory=$PROJECT_DIR" org.codehaus.plexus.classworlds.launcher.Launcher $args
