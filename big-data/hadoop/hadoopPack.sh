#!/bin/bash
set -e
. /var/lib/jenkins/jobs/master.get_branch_repo/workspace/big-data/pack-funcs

productName=hadoop
downloadHadoopAndMakeChanges() {
	initializeVariables $1
	tempDirectory=$BASE/$fileName/opt
	confDirectory=$BASE/$fileName/etc/$productName
	tarFile=hadoop-1.2.1-bin.tar.gz
	# Create directories that are required for the debian package
        mkdir -p $confDirectory

	wget http://www.apache.org/dist/hadoop/core/hadoop-1.2.1/$tarFile -P $tempDirectory
	if [ -f $BASE/$fileName/opt/README ]; then
	        rm $BASE/$fileName/opt/README
	fi
	# unpack tar ball and make changes 
	pushd $tempDirectory
	tar -xpf $tarFile -C .
	rm $tarFile
	mv hadoop*/conf/* $confDirectory
	popd
}

# 2) Get the sources which are downloaded from version control system to local machine to relevant directories to generate the debian package
getSourcesToRelevantDirectories $productName
# 3) Download hadoop tar file and make necessary changes
downloadHadoopAndMakeChanges $productName
# 4) Create the Debian package
generateDebianPackage $productName
