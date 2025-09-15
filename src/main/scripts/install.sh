#!/bin/bash

#
# Copyright (c) 2018-2024 twinlife SA.
# SPDX-License-Identifier: AGPL-3.0-only

#
# Automatic installation & setup of 'twinapp' service
#

# Get the actual path of this package
SCRIPT=$(readlink -f "$0")
PKG_PATH=$(dirname "$SCRIPT")


## Load parameters & common code
. ${PKG_PATH}/parameters.def
. ${PKG_PATH}/common.def

install_release() {
    info "Install software in ${SERVICE_HOME}"

    copy_dir ${PKG_PATH}/bin ${SERVICE_HOME}/bin
    copy_dir ${PKG_PATH}/lib ${SERVICE_HOME}/lib
    
    create_writeable_dir ${SERVICE_CONF}
    
    # Copy configuration files (only if not existing yet)
    cp -n ${PKG_PATH}/config/config-jmx-exporter.yaml \
       ${SERVICE_CONF}/
    
    # Create 'log' directory
    create_writeable_dir ${SERVICE_LOGS}
    
    symlink ${SERVICE_HOME} ${PREFIX}/${SERVICE_NAME}

    chown -R ${SERVICE_USER}:${SERVICE_GROUP} ${SERVICE_HOME}
}

install_systemd_units() {
    info "Install systemd units"
    cp -f ${PKG_PATH}/systemd-units/${SERVICE_NAME}.service /etc/systemd/system/

    systemctl daemon-reload
    systemctl enable ${SERVICE_NAME}
}

# Check that current user is actually 'root'
if is_not_root; then
    fatal "User must be root"
    exit 1
fi

create_user ${SERVICE_USER}

install_release

# Install systemd units
install_systemd_units

info "Installation of 'twinapp' service in ${PREFIX}/${SERVICE_NAME} done."
