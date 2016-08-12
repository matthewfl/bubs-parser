#!/bin/bash
#SBATCH --job-name=bubs-parser
#SBATCH --partition=commons
#SBATCH --export=ALL
#SBATCH --nodes=1 --exclusive
#SBATCH --mem-per-cpu=6000m
#SBATCH --cpus-per-task=12
#SBATCH --time=00:10:00
module load Java/1.8.0_45

## This file should be located in your home directory
##To run: sbatch bubs-parser.slurm
##To check job status: squeue -u $USER
##When job finishes a file "slurm-{jobId}.out" will appear in your home directory


##Plz modify these per your home dir setup
BUBS_HOME=/home/$USER/bubs/running/
#BUBS_PARSER=$BUBS_HOME/build-dist
#BUBS_MODEL=$BUBS_HOME/models
GRAMMAR=$BUBS_HOME/wsj_l0mm_16_.55_6.gr.gz
DATA=$BUBS_HOME/parse.22.sl.small.mrg

NUM_THREADS=1
echo "Bubs parser Test1 on:"
echo $SLURM_JOB_NODELIST

srun java -Xmx5500m -server -cp $BUBS_HOME/bubs-parser.jar edu.ohsu.cslu.parser.ParserDriver -g $GRAMMAR -xt $NUM_THREADS -p Agenda -if Tree -O overParseTune=1.00001 $DATA

echo "job finished"
