set terminal postscript eps color
set key left top box
set grid
set title "Numerische Integration mit Hadoop & OpenCL (f(x)=xsinx)"
set output "ni_xsinx_speedup_ic.eps"
set ylabel "Speedup"
set xlabel "Aufloesung"

set boxwidth 1.00 absolute

set style fill solid 1.00 border -1
#set style data histogram
#set style histogram cluster gap 2

#set mxtics 2
set mytics 2
#set ytics 5 nomirror
#set xtics 16 nomirror

set offset 100,100,0,0
#set xrange[0:144]
set yrange[0:*]

x=1
y="6"

plot \
'times_1000ic.csv' u x:($6/$11) t " 1000 Intervalle" w linespoints