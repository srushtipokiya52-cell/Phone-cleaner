#!/usr/bin/perl
# Generates res/values*/strings.xml from tools/translations_*.txt
#
# Each line:  English||Hindi||Gujarati||Spanish||French||German||Portuguese||Arabic||Indonesian||Russian
# The resource name is derived from the English text (same rule as core/Tr.kt `slug`).
#
#   perl tools/gen_strings.pl
use strict;
use warnings;
use File::Basename qw(dirname);

my $root = dirname(dirname(__FILE__));
my @dirs = ('values', 'values-hi', 'values-gu', 'values-es', 'values-fr', 'values-de', 'values-pt', 'values-ar', 'values-in', 'values-ru');

sub slug {
    my ($s) = @_;
    $s =~ s/\x{2026}|\xE2\x80\xA6/ ellipsis /g;
    $s =~ s/[^A-Za-z0-9]+/_/g;
    $s =~ s/^_+//;
    $s =~ s/_+$//;
    return substr($s, 0, 64);
}

sub esc {
    my ($s) = @_;
    $s =~ s/&/&amp;/g;
    $s =~ s/</&lt;/g;
    $s =~ s/'/\\'/g;
    $s =~ s/"/\\"/g;
    $s =~ s/^([@?])/\\$1/;
    return $s;
}

my @rows;
my %seen;
for my $f (glob("$root/tools/translations_*.txt")) {
    open(my $fh, '<:raw', $f) or die "$f: $!";
    while (my $line = <$fh>) {
        $line =~ s/[\r\n]+$//;
        next if $line =~ /^\s*$/;
        my @c = split(/\|\|/, $line, -1);
        die "Row has " . scalar(@c) . " columns (need 10): $line\n" if @c < 10;
        my $key = slug($c[0]);
        die "Duplicate key '$key' for: $c[0]\n" if $seen{$key}++;
        push @rows, [$key, @c[0 .. 9]];
    }
    close $fh;
}

for my $i (0 .. $#dirs) {
    my $dir = "$root/app/src/main/res/$dirs[$i]";
    mkdir $dir unless -d $dir;
    open(my $out, '>:raw', "$dir/strings.xml") or die "$dir: $!";
    print $out qq{<?xml version="1.0" encoding="utf-8"?>\n<resources>\n};
    if ($i == 0) {
        print $out qq{    <string name="app_name">Phone Cleaner</string>\n    <string name="back">Back</string>\n};
    } else {
        print $out qq{    <string name="app_name">} . esc($rows[0][0] eq 'Done' ? 'Phone Cleaner' : 'Phone Cleaner') . qq{</string>\n};
    }
    for my $r (@rows) {
        my $text = $r->[1 + $i];
        $text = $r->[1] if !defined($text) || $text eq '';
        print $out '    <string name="tr_' . $r->[0] . '">' . esc($text) . "</string>\n";
    }
    print $out "</resources>\n";
    close $out;
}
print "Wrote " . scalar(@rows) . " strings x " . scalar(@dirs) . " languages\n";
