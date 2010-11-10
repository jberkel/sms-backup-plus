raise "Needs JRuby 1.5" unless RUBY_PLATFORM =~ /java/
require 'ant'
require 'rbconfig'
require 'rake/clean'
require 'rexml/document'

CLEAN.include('tmp', 'bin')

ant_import

apk = ant.properties['out.debug.package']
[:device, :emu].each do |t|
  flag = (t == :device ? '-d' : '-e')
  namespace t do
    task :cond_debug do
      unless uptodate?(apk, FileList['src/**/*.java', 'res/**/*', 'assets/**/*'])
        Rake::Task[:debug].invoke
      end
    end

    task :install => :cond_debug do
      sh "adb #{flag} install -r #{apk}"
    end

    task :uninstall do
      sh "adb #{flag} uninstall #{package}"
    end
    task :reinstall => [:uninstall, :install]

    task :db do
      sh "adb #{flag} pull /data/data/com.android.providers.telephony/databases/mmssms.db ."
    end

    namespace :prefs do
      task :pull do
        sh "adb #{flag} pull /data/data/#{package}/shared_prefs/#{package}_preferences.xml ."
      end
      task :push do
        sh "adb #{flag} push #{package}_preferences.xml /data/data/#{package}/shared_prefs/#{package}_preferences.xml"
      end
    end
  end
end

task :check_version do
  # make sure new version is propagated everywhere
  raise "CHANGES not updated" unless IO.read('CHANGES') =~ /#{version}/
  raise "README.md not updated" unless IO.read('README.md') =~ /#{version}\.apk/
  raise "about not updated" unless IO.read('assets/about.html') =~ /#{version}/
end

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
  Exec.system "aspell", "--mode", "html", "--dont-backup", "check", 'README.md'
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

    case Config::CONFIG['host_os']
      when /darwin/
        puts "Launching: open #{outfile}"
        sh "open", outfile
     end
  end
end

def manifest
  @manifest ||= REXML::Document.new(File.read('AndroidManifest.xml'))
end

def strings(name)
  @strings ||= REXML::Document.new(File.read('res/values/strings.xml'))
  value = @strings.elements["//string[@name='#{name.to_s}']"] or raise "string '#{name}' not found in strings.xml"
  value.text
end

def package() manifest.root.attribute('package') end
def version() manifest.root.attribute('versionName') end
def app_name()  strings :app_name end

module Exec
  module Java
    def system(file, *args)
      require 'spoon'
      Process.waitpid(Spoon.spawnp(file, *args))
    rescue Errno::ECHILD => e
      raise "error exec'ing #{file}: #{e}"
    end
  end

  module MRI
    def system(file, *args)
      Kernel::system(file, *args) #or raise "error exec'ing #{file}: #{$?}"
    end
  end

  extend RUBY_PLATFORM =~ /java/ ? Java : MRI
end
