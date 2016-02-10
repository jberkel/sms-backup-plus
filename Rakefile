require 'rake/clean'
require 'rbconfig'
require 'rexml/document'

CLEAN.include('tmp', 'bin')

[:device, :emu].each do |t|
  flag = (t == :device ? '-d' : '-e')
    namespace :db do
      task :pull do
        sh "adb #{flag} pull /data/data/com.android.providers.telephony/databases/mmssms.db ."
      end

      task :push do
        sh "adb #{flag} push mmssms.db /data/data/com.android.providers.telephony/databases/mmssms.db"
      end
    end

    namespace :log do
      task :pull do
        sh "adb #{flag} pull /sdcard/sms_backup_plus.log ."
      end
    end

    namespace :prefs do
      desc "get prefs from #{t}"
      task :pull do
        sh "adb #{flag} pull /data/data/#{package}/shared_prefs/#{package}_preferences.xml ."
        sh "adb #{flag} pull /data/data/#{package}/shared_prefs/credentials.xml ."
      end
      task :push do
        sh "adb #{flag} push #{package}_preferences.xml /data/data/#{package}/shared_prefs/#{package}_preferences.xml"
        sh "adb #{flag} push credentials.xml /data/data/#{package}/shared_prefs/credentials.xml"
      end
    end
end

desc "check release"
task :check_version do
  # make sure new version is propagated everywhere
  raise "CHANGES not updated" unless IO.read('CHANGES') =~ /#{version}/
  raise "about not updated" unless IO.read('assets/about.html') =~ /SMS Backup\+ #{version}/
end

desc "perform a release build"
task :release => :check_version do
  sh "mvn clean install -DskipTests -Prelease,smsbackupplus"
end

desc "tag the current version"
task :tag => [:check_version] do
  unless `git branch` =~ /^\* master$/
    puts "You must be on the master branch to release!"
    exit!
  end
  sh "git commit --allow-empty -a -m 'Release #{version}'"
  sh "git tag #{version}"
  sh "git push origin master --tags"
end

desc "spellcheck README"
task :spell do
  sh "aspell", "--mode", "html", "--dont-backup", "check", 'README.md'
end

namespace :doc do
  desc "Render markdown as if it were shown on github"
  task :preview do
    infile = File.expand_path('README.md')
    outfile = "/tmp/#{File.basename(infile)}.html"
    revision = `git rev-parse HEAD`.strip
    markdown = `which markdown`.strip

    unless $?.success?
      puts "Make sure you have 'markdown' in your path, usage: brew install markdown"
      exit 1
    end

    File.open(outfile, "w") do |out|
      body = `#{markdown} #{infile}`
      template = <<-END
        <html>
          <meta http-equiv="content-type" content="text/html;charset=UTF-8" />
          <meta http-equiv="X-UA-Compatible" content="chrome=1">
          <head>
            <link href="https://assets0.github.com/stylesheets/bundle_common.css?#{revision}" media="screen" rel="stylesheet" type="text/css" />
            <link href="https://assets3.github.com/stylesheets/bundle_github.css?#{revision}" media="screen" rel="stylesheet" type="text/css" />
          </head>
          <body>
            <div id="readme" class="blob">
              <div class="wikistyle">
                #{body}
              </div>
            </div>
          </body>
        </html>
      END
      out.write(template)
    end
    sh "open", outfile
  end
end

def manifest
  @manifest ||= REXML::Document.new(File.read('AndroidManifest.xml'))
end

def package() manifest.root.attribute('package') end
def version() manifest.root.attribute('versionName') end
