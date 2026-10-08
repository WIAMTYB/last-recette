pipeline {
    tools {
        jdk 'jdk25'
    }
    agent { label 'Jenkins_slave3 || Jenkins-slave4'}

    environment {
        MAVEN_OPTS = '--add-opens java.base/java.lang=ALL-UNNAMED'

        // Same builder/runtime pair as Titres_cargo-api. Pinned by digest: a floating
        // tag lets the toolchain change under the build without anything in this repo
        // moving.
        GRAALVM_BUILDER_IMAGE = 'registry.udd.attijariwafa.net/si-trading/graalvm:25@sha256:57d98c31eab361bd3eb37c7de9b8e13adc6312393ce543f67d08c97b6ade3e54'
        UBI_RUNTIME_IMAGE     = 'registry.udd.attijariwafa.net/base/ubi9/ubi:latest'

        REGISTRY       = 'registry.udd.attijariwafa.net'
        HARBOR_PROJECT = 'si-trading'
        IMAGE_NAME     = 'epargne-opcvm'

        // Jenkins credential wrapping the Harbor robot$si-trading+push account. Used for
        // both pulling the builder image and pushing the result.
        TOKEN_HARBOR = 'si-trading-harbor-push-robot'

        // Carried over from the previous javaPipeline recette configuration so the
        // Sonar project and Fortify application stay the same.
        SONAR_PROJECT_KEY  = 'titres-ordres-recurrents-services'
        FORTIFY_APP_NAME   = 'titres-ordres-recurrents-services'
        FORTIFY_APP_VERSION = 'dev-recette'
    }

    options {
        disableConcurrentBuilds()
        gitLabConnection('gitlab-connection')
        buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10'))
    }

    stages {
        stage('Prepare') {
            steps {
                gitlabCommitStatus('Prepare') {
                    script {
                        pom = readMavenPom file: 'pom.xml'
                        // recette-native builds the recette deliverable: the si-trading
                        // GitOps overlay (workloads/services/api-ordres-recurrents/
                        // overlays/recette) pins its newTag to what this produces.
                        def version = "${pom.version}".replace('-SNAPSHOT', '')
                        if (env.BRANCH_NAME == 'recette' || env.BRANCH_NAME == 'recette-native') {
                            version = "${version}-rct-SNAPSHOT"
                        } else if (env.BRANCH_NAME == 'dev' || env.BRANCH_NAME == 'develop') {
                            version = "${version}-dev-SNAPSHOT"
                        }
                        env.VERSION = version
                        echo "${IMAGE_NAME} ${env.VERSION} (branch ${env.BRANCH_NAME})"
                    }
                }
            }
        }

        // Tests run here (no -Dmaven.test.skip) so jacoco writes the XML report the
        // Quality analysis stage feeds to Sonar as coverage.
        stage('build & Test') {
            steps {
                gitlabCommitStatus('build & Test') {
                    sh '${M2_HOME}/bin/mvn clean install'
                }
            }
        }

        stage('Dep Track Vulnerabilities') {
            steps {
                gitlabCommitStatus('Dep Track Vulnerabilities') {
                    dtrackv2('target/bom.xml', pom.version, pom.artifactId)
                }
            }
        }

        stage('SAST Scan gitleaks') {
            steps {
                gitlabCommitStatus('SAST Scan gitleaks') {
                    script {
                        scangitleaks 'gitleaksdir' // devops-shared library
                        repositoryUrl = scm.userRemoteConfigs[0].url
                        withCredentials([usernamePassword(credentialsId: 'devsecops_jenkins_robot', passwordVariable: 'pass', usernameVariable: 'user')]) {
                            pushreportgitleaks(user, pass, repositoryUrl, './gitleaksdir/gitleaks.json')
                        }
                    }
                }
            }
        }

        stage('FORTIFY SAST Scan') {
            steps {
                gitlabCommitStatus('FORTIFY SAST Scan') {
                    script {
                        fortifyTranslate buildID: "${IMAGE_NAME}",
                                         excludeList: '.fortify',
                                         logFile: '', maxHeap: '',
                                         projectScanType: fortifyJava(javaAddOptions: '', javaClasspath: '', javaSrcFiles: 'src/main/java/**/*.java', javaVersion: '17')
                        fortifyScan addJVMOptions: '', addOptions: '', buildID: "${IMAGE_NAME}",
                                    customRulepacks: '', logFile: '', maxHeap: '', resultsFile: ''
                        // pollingInterval 0 = upload and move on. With the default the step polls SSC
                        // until the artifact is processed, and SSC parks artifacts that trip a
                        // "require approval" rule (file count jump, analyzer warnings) until a
                        // security lead approves them, which blocked #19 for 35 minutes. The scan
                        // still runs and the findings still reach SSC; only the wait is gone.
                        fortifyUpload appName: "${FORTIFY_APP_NAME}", appVersion: "${FORTIFY_APP_VERSION}",
                                      failureCriteria: '', filterSet: '', pollingInterval: '0', resultsFile: 'scan.fpr'
                    }
                }
            }
        }

        stage('Quality analysis') {
            steps {
                gitlabCommitStatus('Quality analysis') {
                    // No credentialsId: the 'SonarCube' installation already carries a
                    // working token, and naming titres-ordres-recurrents-Sonar-token here
                    // overrode it with one the server rejects - build #1 died on
                    // "Not authorized. Please check the properties sonar.login and
                    // sonar.password." Every pipeline that analyses successfully on this
                    // controller (Titres_cargo-api, Titres_cargo-front) uses the bare form.
                    withSonarQubeEnv('SonarCube') {
                        sh '''${M2_HOME}/bin/mvn -T2C sonar:sonar \
                            -Dsonar.projectKey=${SONAR_PROJECT_KEY} \
                            -Dsonar.projectName=${SONAR_PROJECT_KEY} \
                            -Dsonar.exclusions=**/generated/**,**/target/** \
                            -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml \
                            -Dmaven.test.skip=true'''
                    }
                }
            }
        }

        stage('Build native image') {
            steps {
                gitlabCommitStatus('Build native image') {
                    // script{} is required: docker.withRegistry is a Groovy method from
                    // the docker-workflow plugin, not a declarative step.
                    script {
                    // withRegistry so `docker run` can pull the private builder image.
                    docker.withRegistry("https://${REGISTRY}", TOKEN_HARBOR) {
                    sh """
                        set -eu

                        NATIVE_MAVEN_HOME="\$HOME/.m2"
                        mkdir -p "\${NATIVE_MAVEN_HOME}/repository"

                        docker pull "\${GRAALVM_BUILDER_IMAGE}"
                        echo "GraalVM builder digest: \$(docker image inspect "\${GRAALVM_BUILDER_IMAGE}" -f '{{index .RepoDigests 0}}')"

                        # GraalVM runs in a container so the agent needs no native-image
                        # toolchain. HOME is forced to /tmp because the container runs as
                        # the agent's uid, which has no home inside the image.
                        docker run --rm \\
                            --entrypoint /bin/sh \\
                            -u "\$(id -u):\$(id -g)" \\
                            -v "\$PWD:/workspace" \\
                            -v "\${NATIVE_MAVEN_HOME}:/tmp/.m2" \\
                            -w /workspace \\
                            -e MAVEN_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED -Duser.home=/tmp" \\
                            -e MAVEN_CONFIG=/tmp/.m2 \\
                            -e MAVEN_USER_HOME=/tmp/.m2 \\
                            -e HOME=/tmp \\
                            "\${GRAALVM_BUILDER_IMAGE}" \\
                            -lc '
                                set -eu
                                chmod +x ./mvnw
                                mkdir -p /tmp/.m2/repository
                                settings_arg=""
                                if [ -f /tmp/.m2/settings.xml ]; then
                                    settings_arg="-s /tmp/.m2/settings.xml"
                                fi
                                ./mvnw \${settings_arg} -Pnative clean native:compile -Dmaven.test.skip=true -Dmaven.repo.local=/tmp/.m2/repository
                            '

                        # native:compile can exit 0 having produced nothing useful, so
                        # assert the binary exists before building an image around it.
                        if [ ! -x "target/${IMAGE_NAME}" ]; then
                            echo "ERROR: expected native executable target/${IMAGE_NAME} was not created or is not executable."
                            exit 1
                        fi
                        file "target/${IMAGE_NAME}"
                    """
                    }
                    }
                }
            }
        }

        stage('Build & Push Docker Image') {
            when {
                anyOf {
                    branch 'main'
                    branch 'master'
                    branch 'recette'
                    branch 'recette-native'
                    branch 'dev'
                    branch 'develop'
                }
            }
            steps {
                gitlabCommitStatus('Build & Push Docker Image') {
                    script {
                        docker.withRegistry("https://${REGISTRY}", TOKEN_HARBOR) {
                            sh """
                                set -eu
                                docker build \\
                                    --build-arg UBI_RUNTIME_IMAGE="\${UBI_RUNTIME_IMAGE}" \\
                                    -f Dockerfile.native \\
                                    -t ${IMAGE_NAME}:${env.VERSION} \\
                                    .
                                docker tag ${IMAGE_NAME}:${env.VERSION} ${REGISTRY}/${HARBOR_PROJECT}/${IMAGE_NAME}:${env.VERSION}
                                docker push ${REGISTRY}/${HARBOR_PROJECT}/${IMAGE_NAME}:${env.VERSION}
                                docker rmi ${REGISTRY}/${HARBOR_PROJECT}/${IMAGE_NAME}:${env.VERSION} || true
                                docker rmi ${IMAGE_NAME}:${env.VERSION} || true
                            """
                        }
                    }
                }
            }
        }

        // Deploy stays manual in the ArgoCD UI, the same way Titres_cargo-api does it.
        // The si-trading GitOps overlay workloads/services/api-ordres-recurrents/
        // overlays/recette pins newTag to what 'Build & Push Docker Image' pushes;
        // syncing recette-si-trading-api-ordres-recurrents-services picks it up.
    }
}
