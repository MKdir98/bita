{{/*
Expand the name of the chart.
*/}}
{{- define "bita-esb.name" -}}
{{- printf "bita-esb-%s" .Values.serviceId | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
*/}}
{{- define "bita-esb.fullname" -}}
{{- printf "bita-esb-%s" .Values.serviceId | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "bita-esb.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "bita-esb.labels" -}}
helm.sh/chart: {{ include "bita-esb.chart" . }}
{{ include "bita-esb.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: bita-platform
{{- end }}

{{/*
Selector labels
*/}}
{{- define "bita-esb.selectorLabels" -}}
app.kubernetes.io/name: bita-esb-core
app.kubernetes.io/instance: {{ .Release.Name }}
bita.ir/service-id: {{ .Values.serviceId | quote }}
{{- end }}
