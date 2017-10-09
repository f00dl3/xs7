# xs7
XML Weather Station Parser - in Java

I did not include the MySQL connector because numerous examples can easily be found online.

- update 9/25/17 - xsWorkerMETARStream.java replaces xsWorkerMETAR.java. Much faster. No threadding required.
- update 10/9/17 - Now collects data from "Wunder" stations every 2 minutes. From all METAR & XML stations twice per hour. Runs model analisys once per hour. Longest script execution time is 7 minutes, with the 2 minute kickoffs taking about 5-10 seconds and the half hour increment taking about 4 minutes.

Last code synch / updated: 2017-10-09
