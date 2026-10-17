# One-off refactor: wraps hard-coded UI text in tr()/trf() so it can be translated.
use strict; use warnings;
my $src = 'app/src/main/java/com/phone/cleanerapp/storage/mediafiles';
my @files = (glob("$src/ui/*.kt"), glob("$src/data/*.kt"), glob("$src/service/*.kt"));
my %content; for my $f (@files) { open my $fh,'<:raw',$f or die; local $/; $content{$f}=<$fh>; close $fh; }
# 1) explicit templates
open my $pf,'<:raw','tools/kt_pairs.txt' or die;
while (my $line=<$pf>) { chomp $line; $line =~ s/\r$//; next unless $line =~ /\S/;
  my ($file,$old,$new) = split / ==>> /, $line, 3; die "bad line: $line" unless defined $new;
  my $hit=0;
  for my $f (@files) { next unless $f =~ m{/\Q$file\E$}; my $c=$content{$f}; my $n=0; my $pos=0;
    while ((my $i=index($c,$old,$pos))>=0) { substr($c,$i,length($old))=$new; $pos=$i+length($new); $n++; }
    if($n){ $content{$f}=$c; $hit+=$n; } }
  print "NO MATCH: $line\n" unless $hit;
}
# 2) plain strings
my %slug; my @plain;
sub slugf { my ($s)=@_; $s =~ s/\xE2\x80\xA6/ ellipsis /g; $s =~ s/[^A-Za-z0-9]+/_/g; $s =~ s/^_+//; $s =~ s/_+$//; substr($s,0,64) }
for my $t (glob("tools/translations_*.txt")) { open my $fh,'<:raw',$t or die; while(<$fh>){ chomp; s/\r$//; my @c=split /\|\|/,$_,-1; next unless @c>=10; push @plain,$c[0] unless $c[0] =~ /%\d/; } }
my $alt = join '|', map { quotemeta } sort { length($b) <=> length($a) } @plain;
for my $f (@files) {
  my @out;
  for my $l (split /(?<=\n)/, $content{$f}) {
    my $skip = ($l =~ /enum class|^\s*[A-Z_]+\("|\.contains\(|const val|^import |^package /);
    unless ($skip) { $l =~ s/(?<!tr\()(?<!trf\()"($alt)"/tr("$1")/g; }
    push @out,$l;
  }
  my $c = join '', @out;
  if ($c =~ /\btrf?\(/ && $c !~ /import com\.phone\.cleanerapp\.storage\.mediafiles\.core\.tr\b/ && $f !~ m{/core/}) {
    $c =~ s/(package [^\n]+\n)/$1\nimport com.phone.cleanerapp.storage.mediafiles.core.tr\nimport com.phone.cleanerapp.storage.mediafiles.core.trf\n/;
  }
  open my $o,'>:raw',$f or die; print $o $c; close $o;
}
print "done\n";
