

import { Box, Breadcrumbs, Button, ButtonGroup, Link, Tab, Tabs, Tooltip, Typography } from '@mui/material'
import React, { Fragment, useEffect, useState } from 'react'
import { Redirect } from 'react-router-dom'
import { get } from '../../api'
import Header from '../../components/Header'
import Spinner from '../../components/Spinner'
import Ontology from '../../model/Ontology'
import { Link as RouterLink } from 'react-router-dom'
import { IconButton } from '@mui/material';
import { AccountTree, Share } from '@mui/icons-material'
import FormatListBulletedIcon from '@mui/icons-material/FormatListBulleted';
import EntityTree from './EntityTree'
import Entity from '../../model/Entity'

export default function EntityPage(props:{ontologyId:string,entityUri:string}) {

    let { ontologyId, entityUri } = props

    let [ ontology, setOntology ] = useState<Ontology|undefined>(undefined)
    let [ term, setTerm ] = useState<Entity|undefined>(undefined)
    let [ viewMode, setViewMode ] = useState<'tree'|'graph'>('tree')

    useEffect(() => {

	async function fetchOntology() {
		let ontologyProperties = await get<any>(`/api/v2/ontologies/${ontologyId}`)
		setOntology(new Ontology(ontologyProperties))
	}

	async function fetchTerm() {
		let doubleEncodedTermUri = encodeURIComponent(encodeURIComponent(entityUri))
		let termProperties = await get<any>(`/api/v2/ontologies/${ontologyId}/terms/${doubleEncodedTermUri}`)
		setTerm(entityFromProperties(termProperties))
	}

	fetchOntology()
	fetchTerm()

    }, [])

    return   <Fragment>
    <Header section='ontologies' />
    <main>

	{ renderTermPage() }

    </main>
</Fragment>


function renderTermPage() {

    if(!ontology || !term) {
	return <Spinner/>
    }

    return  <Fragment>

	<Breadcrumbs>
		<Link color="inherit" component={RouterLink} to="/ontologies">
			Ontologies
		</Link>
		<Link color="inherit" component={RouterLink} to={"/ontologies/"+ontologyId}>
			{ontology.getName()}
		</Link>
		<Typography color="textPrimary">
		{
			({
				'class': 'Classes',
				'property': 'Properties',
				'individual': 'Individuals'
			})[term.getType()]
		}
		</Typography>
		<Typography color="textPrimary">{term.getName()}</Typography>
	</Breadcrumbs>

	<h1>{term!.getName()}</h1>

	<Box>
		<p>
		{term!.getDescription()}
		</p>
	</Box>
<br/>
	<ButtonGroup variant="contained" aria-label="outlined primary button group">
		<Tooltip title="Tree view" placement="top">
		  <Button
			variant={viewMode === 'tree' ? 'contained' : 'outlined'}
			onClick={() => setViewMode('tree')}
			>
			<AccountTree />
		  </Button>
		  </Tooltip>
		<Tooltip title="Graph view" placement="top">
		  <Button
			variant={viewMode === 'graph' ? 'contained' : 'outlined'}
			onClick={() => setViewMode('graph')}
			>
			<Share />
		   </Button>
		   </Tooltip>
	  </ButtonGroup>

	<br/>

<Box py={2}>
	{ viewMode === 'tree' ?
		<EntityTree ontologyId={ontologyId} entityType={({
			'class': 'classes',
			'property': 'properties',
			'individual': 'individuals'
		})[term.getType()]} 
		startingNode={term}
		/>
		: <div/>
	}
	</Box>

</Fragment>


}

}


function entityFromProperties(termProperties: any): React.SetStateAction<Entity | undefined> {
	throw new Error('Function not implemented.')
}
